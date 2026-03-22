package com.wiyuka.acceleratedrecoiling.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.wiyuka.acceleratedrecoiling.config.FoldConfig;
import com.wiyuka.acceleratedrecoiling.natives.NativeInterface;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

// Paper 的新 Command API 需要
public class ToggleFoldCommand implements BasicCommand {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();

        if(!(stack.getSender().hasPermission("acceleratedrecoiling.admin") || stack.getSender().isOp())) return;

        if (args.length == 0) {
            sender.sendMessage(Component.text("Missing arguments.", NamedTextColor.RED));
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "check" -> { checkConfig(sender); return; }
            case "save" -> { save(sender); return; }
            case "updateconfig" -> { updateConfig(sender); return; }
        }

        // 动态读取并设置 Config 的字段
        for (Field field : FoldConfig.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) continue;

            if (field.getName().equalsIgnoreCase(args[0])) {
                handleField(sender, field, args);
                return;
            }
        }

        sender.sendMessage(Component.text("Unknown subcommand or config field: " + args[0], NamedTextColor.RED));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            suggestions.add("check");
            suggestions.add("save");
            suggestions.add("updateConfig");

            for (Field field : FoldConfig.class.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)) {
                    suggestions.add(field.getName());
                }
            }
            return filterStartsWith(suggestions, args[0]);

        } else if (args.length == 2) {
            String fieldName = args[0];
            for (Field field : FoldConfig.class.getDeclaredFields()) {
                if (field.getName().equalsIgnoreCase(fieldName)) {
                    if (field.getType() == boolean.class) {
                        suggestions.add("true");
                        suggestions.add("false");
                    }
                    return filterStartsWith(suggestions, args[1]);
                }
            }
        }

        return List.of();
    }

    private List<String> filterStartsWith(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    private void handleField(CommandSender sender, Field field, String[] args) {
        field.setAccessible(true);
        Class<?> type = field.getType();

        try {
            if (type == boolean.class) {
                if (args.length == 1) {
                    // 无参数时直接翻转布尔值
                    boolean currentValue = field.getBoolean(null);
                    setFieldValue(sender, field, !currentValue);
                } else {
                    boolean val = Boolean.parseBoolean(args[1]);
                    setFieldValue(sender, field, val);
                }
            } else if (args.length > 1) {
                if (type == int.class) {
                    setFieldValue(sender, field, Integer.parseInt(args[1]));
                } else if (type == float.class) {
                    setFieldValue(sender, field, Float.parseFloat(args[1]));
                } else if (type == double.class) {
                    setFieldValue(sender, field, Double.parseDouble(args[1]));
                }
            } else {
                sender.sendMessage(Component.text("Missing value for " + field.getName(), NamedTextColor.RED));
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid number format.", NamedTextColor.RED));
        } catch (IllegalAccessException e) {
            sender.sendMessage(Component.text("Cannot access config field.", NamedTextColor.RED));
        }
    }

    private void updateConfig(CommandSender sender) {
        NativeInterface.applyConfig();
        sender.sendMessage(Component.text("Config applied via NativeInterface.", NamedTextColor.GREEN));
    }

    private void setFieldValue(CommandSender sender, Field field, Object newValue) {
        try {
            field.set(null, newValue);
            sendSuccessMessage(sender, field.getName(), newValue);
            NativeInterface.applyConfig();
        } catch (IllegalAccessException e) {
            sender.sendMessage(Component.text("Failed to modify config: " + e.getMessage(), NamedTextColor.RED));
            e.printStackTrace();
        }
    }

    private void sendSuccessMessage(CommandSender sender, String configName, Object newValue) {
        // 注意：Adventure API 的 Component 是不可变的(Immutable)，append 会返回新的对象
        Component message = Component.text("Config ", NamedTextColor.GRAY)
                .append(Component.text(configName, NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" updated to ", NamedTextColor.GRAY));

        if (newValue instanceof Boolean boolValue) {
            message = message.append(Component.text(String.valueOf(boolValue),
                    boolValue ? NamedTextColor.GREEN : NamedTextColor.RED));
        } else {
            message = message.append(Component.text(String.valueOf(newValue), NamedTextColor.AQUA));
        }

        sender.sendMessage(message);
    }

    private Component buildConfigLine(String configName, Object value) {
        Component line = Component.text("  " + configName + ": ", NamedTextColor.GRAY);

        if (value instanceof Boolean boolValue) {
            line = line.append(Component.text(String.valueOf(boolValue),
                    boolValue ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD));
        } else {
            line = line.append(Component.text(String.valueOf(value),
                    NamedTextColor.AQUA, TextDecoration.BOLD));
        }

        return line.append(Component.text("\n"));
    }

    private void checkConfig(CommandSender sender) {
        Component message = Component.text("Accelerated Recoiling", NamedTextColor.AQUA)
                .append(Component.text("\n--------------------\n", NamedTextColor.DARK_GRAY));

        for (Field field : FoldConfig.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers)) continue;

            field.setAccessible(true);
            try {
                Object value = field.get(null);
                message = message.append(buildConfigLine(field.getName(), value));
            } catch (IllegalAccessException ignored) {
            }
        }

        message = message.append(Component.text("--------------------", NamedTextColor.DARK_GRAY));
        sender.sendMessage(message);
    }

    private void save(CommandSender sender) {
        // 建议将其放入插件的 DataFolder 中，此处为了与你原版逻辑一致仍使用原路径名
        File targetFile = new File("acceleratedRecoiling.json");
        JsonObject jsonObject = new JsonObject();

        for (Field field : FoldConfig.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) continue;

            field.setAccessible(true);
            try {
                Object value = field.get(null);
                String jsonKey = field.getName();
                SerializedName serializedName = field.getAnnotation(SerializedName.class);

                if (serializedName != null) jsonKey = serializedName.value();
                if (value instanceof Boolean bool) jsonObject.addProperty(jsonKey, bool);
                else if (value instanceof Number number) jsonObject.addProperty(jsonKey, number);
                else if (value instanceof String string) jsonObject.addProperty(jsonKey, string);

            } catch (IllegalAccessException ignored) {
            }
        }

        try (FileWriter writer = new FileWriter(targetFile)) {
            GSON.toJson(jsonObject, writer);

            Component message = Component.text("Config saved ", NamedTextColor.GREEN)
                    .append(Component.text(targetFile.getName(), NamedTextColor.AQUA, TextDecoration.BOLD));
            sender.sendMessage(message);

        } catch (IOException e) {
            Component message = Component.text("Failed to save config file: ", NamedTextColor.RED)
                    .append(Component.text(e.getMessage(), NamedTextColor.WHITE));
            sender.sendMessage(message);
            e.printStackTrace();
        }
    }
}