package com.wiyuka.acceleratedrecoiling.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.wiyuka.acceleratedrecoiling.config.FoldConfig; // 假设你的配置类在这里
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ToggleFoldCommand implements BasicCommand {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        // 权限检查 (对应原本的 requires(permission(2)))
        // 建议在 plugin.yml 中定义该权限，这里假设权限名为 acceleratedrecoiling.admin
        if (!stack.getSender().hasPermission("acceleratedrecoiling.admin")) {
            stack.getSender().sendMessage(Component.text("You do not have permission to run this command.", NamedTextColor.RED));
            return;
        }

        if (args.length == 0) {
            stack.getSender().sendMessage(Component.text("Usage: /togglefold <check|save|option> [value]", NamedTextColor.RED));
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "check" -> checkConfig(stack);
            case "save" -> save(stack);

            // 布尔值设置
            case "enableentitycollision" -> handleBoolean(stack, args, "enableEntityCollision", v -> FoldConfig.enableEntityCollision = v);
            case "enableentitygetteroptimization" -> handleBoolean(stack, args, "enableEntityGetterOptimization", v -> FoldConfig.enableEntityGetterOptimization = v);

            // 整数设置
            case "maxcollision" -> handleInt(stack, args, "maxCollision", v -> FoldConfig.maxCollision = v);

            default -> stack.getSender().sendMessage(Component.text("Unknown subcommand: " + subCommand, NamedTextColor.RED));
        }
    }

    // --- Tab 补全建议 (可选，为了体验更好) ---
    @Override
    public @NotNull List<String> suggest(@NotNull CommandSourceStack commandSourceStack, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("check", "save", "enableEntityCollision", "enableEntityGetterOptimization", "gridSize", "maxCollision", "gpuIndex", "useCPU");
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.startsWith("enable") || sub.equals("usecpu")) {
                return List.of("true", "false");
            }
        }
        return Collections.emptyList();
    }

    // --- 逻辑处理方法 ---

    @FunctionalInterface
    interface BooleanSetter { void set(boolean val); }

    @FunctionalInterface
    interface IntSetter { void set(int val); }

    private void handleBoolean(CommandSourceStack stack, String[] args, String configName, BooleanSetter setter) {
        if (args.length < 2) {
            stack.getSender().sendMessage(Component.text("Usage: /togglefold " + configName + " <true|false>", NamedTextColor.RED));
            return;
        }
        // 解析 true/false
        String input = args[1].toLowerCase();
        if (!input.equals("true") && !input.equals("false")) {
            stack.getSender().sendMessage(Component.text("Invalid boolean value. Use true or false.", NamedTextColor.RED));
            return;
        }
        boolean value = Boolean.parseBoolean(input);
        setter.set(value);
        sendSuccessMessage(stack, configName, value);
    }

    private void handleInt(CommandSourceStack stack, String[] args, String configName, IntSetter setter) {
        if (args.length < 2) {
            stack.getSender().sendMessage(Component.text("Usage: /togglefold " + configName + " <integer>", NamedTextColor.RED));
            return;
        }
        try {
            int value = Integer.parseInt(args[1]);
            setter.set(value);
            sendSuccessMessage(stack, configName, value);
        } catch (NumberFormatException e) {
            stack.getSender().sendMessage(Component.text("Invalid integer value: " + args[1], NamedTextColor.RED));
        }
    }

    // --- 消息构建与功能实现 ---

    private void sendSuccessMessage(CommandSourceStack stack, String configName, Object newValue) {
        Component message = Component.text("Config ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(configName, NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" updated to ", NamedTextColor.GRAY));

        if (newValue instanceof Boolean boolValue) {
            message = message.append(Component.text(String.valueOf(boolValue), boolValue ? NamedTextColor.GREEN : NamedTextColor.RED));
        } else {
            message = message.append(Component.text(String.valueOf(newValue), NamedTextColor.AQUA));
        }

        stack.getSender().sendMessage(message);
    }

    private Component buildConfigLine(String configName, Object value) {
        Component line = Component.text("  " + configName + ": ", NamedTextColor.GRAY);

        if (value instanceof Boolean boolValue) {
            line = line.append(Component.text(String.valueOf(boolValue))
                    .color(boolValue ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .decorate(TextDecoration.BOLD));
        } else {
            line = line.append(Component.text(String.valueOf(value))
                    .color(NamedTextColor.AQUA)
                    .decorate(TextDecoration.BOLD));
        }
        return line.append(Component.newline());
    }

    private void checkConfig(CommandSourceStack stack) {
        Component message = Component.text("Current Accelerated Recoiling Config", NamedTextColor.WHITE)
                .append(Component.text("\n--------------------\n", NamedTextColor.DARK_GRAY));

        message = message.append(buildConfigLine("enableEntityCollision", FoldConfig.enableEntityCollision));
        message = message.append(buildConfigLine("enableEntityGetterOptimization", FoldConfig.enableEntityGetterOptimization));
        message = message.append(buildConfigLine("maxCollision", FoldConfig.maxCollision));

        message = message.append(Component.text("--------------------", NamedTextColor.DARK_GRAY));

        stack.getSender().sendMessage(message);
    }

    private void save(CommandSourceStack stack) {
        // 注意：这里保存到服务器根目录，建议改为 plugin.getDataFolder() 下
        File targetFile = new File("acceleratedRecoiling.json");

        ConfigData data = new ConfigData(
                FoldConfig.enableEntityCollision,
                FoldConfig.enableEntityGetterOptimization,
                FoldConfig.maxCollision
        );

        try (FileWriter writer = new FileWriter(targetFile)) {
            GSON.toJson(data, writer);

            Component message = Component.text("Config saved ", NamedTextColor.GREEN)
                    .append(Component.text(targetFile.getName(), NamedTextColor.AQUA, TextDecoration.BOLD));
            stack.getSender().sendMessage(message);

        } catch (IOException e) {
            Component message = Component.text("Failed to save config file: ", NamedTextColor.RED)
                    .append(Component.text(e.getMessage(), NamedTextColor.WHITE));
            stack.getSender().sendMessage(message);
            e.printStackTrace();
        }
    }

    // --- 数据类 ---

    private static class ConfigData {
        @SerializedName("useFold")
        public boolean enableEntityCollision;
        public boolean enableEntityGetterOptimization;
        public int maxCollision;

        public ConfigData(boolean enableEntityCollision, boolean enableEntityGetterOptimization, int maxCollision) {
            this.enableEntityCollision = enableEntityCollision;
            this.enableEntityGetterOptimization = enableEntityGetterOptimization;
            this.maxCollision = maxCollision;
        }
    }
}