package com.townyreborn.staffconnector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;

final class CommandAudit {
    private static final Pattern DURATION_PART = Pattern.compile("(?i)(\\d+)(mo|y|w|d|h|m|s)");
    private static final Set<String> SECRET_COMMANDS = Set.of(
            "login", "l", "register", "reg", "changepassword", "changepass", "password", "passwd",
            "setpassword", "auth", "2fa", "otp", "totp", "pin", "link", "verify", "discord", "discordsrv"
    );
    private static final Set<String> PRIVATE_MESSAGE_COMMANDS = Set.of(
            "msg", "tell", "w", "whisper", "message", "pm", "m", "emsg", "etell", "ewhisper"
    );
    private static final Set<String> REPLY_COMMANDS = Set.of("r", "reply", "ereply");

    private CommandAudit() {
    }

    static Parsed parse(String commandLine) {
        String normalized = commandLine == null ? "" : commandLine.trim();
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.isEmpty()) return null;

        String[] split = normalized.split("\\s+");
        String originalLabel = split[0].toLowerCase(Locale.ROOT);
        int namespace = originalLabel.indexOf(':');
        String label = namespace >= 0 ? originalLabel.substring(namespace + 1) : originalLabel;
        List<String> arguments = new ArrayList<>(Arrays.asList(split).subList(1, split.length));
        List<String> positional = new ArrayList<>();
        for (String argument : arguments) {
            if (positional.isEmpty() && argument.startsWith("-") && argument.length() > 1) continue;
            positional.add(argument);
        }

        String target = positional.isEmpty() ? null : cleanTarget(positional.get(0));
        String punishmentType = null;
        String reason = null;
        Long duration = null;
        int reasonStart = 1;

        switch (label) {
            case "ban" -> punishmentType = "ban";
            case "tempban" -> {
                punishmentType = "tempban";
                if (positional.size() > 1) {
                    duration = parseDuration(positional.get(1));
                    reasonStart = 2;
                }
            }
            case "mute" -> punishmentType = "mute";
            case "tempmute" -> {
                punishmentType = "mute";
                if (positional.size() > 1) {
                    duration = parseDuration(positional.get(1));
                    reasonStart = 2;
                }
            }
            case "warn" -> punishmentType = "warning";
            case "kick" -> punishmentType = "kick";
            default -> {
            }
        }
        if (punishmentType != null) {
            reason = joinFrom(positional, reasonStart);
            if (reason == null || reason.isBlank()) reason = "No reason supplied";
        }
        return new Parsed(label, normalized, List.copyOf(arguments), target, punishmentType, reason, duration);
    }

    static Long parseDuration(String input) {
        if (input == null) return null;
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("perm") || normalized.equals("permanent") || normalized.equals("forever")) return null;
        Matcher matcher = DURATION_PART.matcher(normalized);
        long seconds = 0;
        int consumed = 0;
        while (matcher.find()) {
            if (matcher.start() != consumed) return null;
            consumed = matcher.end();
            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException exception) {
                return null;
            }
            long factor = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "y" -> 31_536_000L;
                case "mo" -> 2_592_000L;
                case "w" -> 604_800L;
                case "d" -> 86_400L;
                case "h" -> 3_600L;
                case "m" -> 60L;
                default -> 1L;
            };
            try {
                seconds = Math.addExact(seconds, Math.multiplyExact(amount, factor));
            } catch (ArithmeticException exception) {
                return Long.MAX_VALUE;
            }
        }
        return consumed == normalized.length() && seconds > 0 ? seconds : null;
    }

    static boolean containsSecret(String label) {
        return SECRET_COMMANDS.contains(label);
    }

    static boolean isPrivateMessage(String label) {
        return PRIVATE_MESSAGE_COMMANDS.contains(label);
    }

    static boolean isReply(String label) {
        return REPLY_COMMANDS.contains(label);
    }

    static String safeCommand(Parsed parsed, boolean includeArguments, boolean channelMessage) {
        if (containsSecret(parsed.label())) return parsed.label() + " [REDACTED]";
        if (!includeArguments || parsed.arguments().isEmpty()) return parsed.label();
        if (isPrivateMessage(parsed.label())) {
            String target = parsed.arguments().isEmpty() ? "" : " " + parsed.arguments().get(0);
            return parsed.label() + target + " [message logged separately]";
        }
        if (isReply(parsed.label()) || channelMessage) {
            return parsed.label() + " [message logged separately]";
        }
        return parsed.normalizedCommand();
    }

    static String privateMessageBody(Parsed parsed) {
        if (!isPrivateMessage(parsed.label()) || parsed.arguments().size() < 2) return null;
        return String.join(" ", parsed.arguments().subList(1, parsed.arguments().size()));
    }

    static String replyBody(Parsed parsed) {
        if (!isReply(parsed.label()) || parsed.arguments().isEmpty()) return null;
        return String.join(" ", parsed.arguments());
    }

    private static String cleanTarget(String target) {
        if (target == null) return null;
        String result = target.trim();
        return result.length() > 96 ? result.substring(0, 96) : result;
    }

    private static String joinFrom(List<String> values, int index) {
        if (index >= values.size()) return null;
        return String.join(" ", values.subList(index, values.size()));
    }

    record Parsed(String label, String normalizedCommand, List<String> arguments, String target,
                  String punishmentType, String reason, Long durationSeconds) {
    }
}
