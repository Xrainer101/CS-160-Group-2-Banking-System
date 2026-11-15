package com.example.onlinebanking_cs160;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import database.DatabaseManager;

/**
 * Pulls the important numbers out of the text that ML Kit reads from a photo of a check:
 *  - the amount            (the "$ 1,250.00" box)
 *  - the check number      (top right, "No. 1043", and again on the bottom line)
 *  - the routing number    (9 digits on the bottom "MICR" line, checked with the routing checksum)
 *  - the account number    (after the routing number on the bottom line)
 *
 * Text recognition isn't perfect, so the Deposit Check screen shows what was found in editable
 * boxes for the user to confirm before anything is deposited.
 */
public final class CheckParser {
    private CheckParser() { }

    public static class Result {
        public Double amount;          // null if none found
        public String checkNumber;     // null if none found
        public String routingNumber;   // null if none found
        public String accountNumber;   // null if none found

        public boolean foundAnything() {
            return amount != null || checkNumber != null || routingNumber != null || accountNumber != null;
        }
    }

    // "$1,250.00", "$ 1250.00", "**1,250.00**", "1,250.00" (2 decimals, optional $ and thousands commas)
    private static final Pattern AMOUNT = Pattern.compile(
            "(\\$\\s*\\**\\s*)?(\\d{1,3}(?:,\\d{3})+|\\d+)\\s?[.,]\\s?(\\d{2})(?!\\d)");
    // "No. 1043", "No 1043", "Check #1043", "#1043", "CHECK NO. 1043"
    private static final Pattern LABELLED_CHECK_NUMBER = Pattern.compile(
            "(?:\\bno\\.?|#|check\\s*(?:no\\.?|number|#))\\s*:?\\s*(\\d{3,6})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern STANDALONE_NUMBER = Pattern.compile("^\\s*(\\d{3,6})\\s*$");
    private static final Pattern DIGIT_RUN = Pattern.compile("\\d+");

    /** lines: the text lines ML Kit found, top to bottom. */
    public static Result parse(List<String> lines) {
        Result result = new Result();
        if (lines == null) return result;

        findMicrLine(lines, result);
        result.amount = findAmount(lines);

        // Check number: a labelled one ("No. 1043") wins, then a short number alone on a line
        // near the top (where checks print it), then the one from the bottom line.
        String labelled = null;
        String standalone = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = LABELLED_CHECK_NUMBER.matcher(line);
            if (labelled == null && m.find()) labelled = m.group(1);
            Matcher s = STANDALONE_NUMBER.matcher(line);
            if (standalone == null && i < Math.max(4, lines.size() / 2) && s.find()) standalone = s.group(1);
        }
        if (labelled != null) result.checkNumber = stripZeros(labelled);
        else if (standalone != null) result.checkNumber = stripZeros(standalone);
        return result;
    }

    /**
     * The bottom line of a check is printed in a special font (MICR):  ⑆322271627⑆ 000123456789⑈ 1043
     * OCR reads the symbols as odd characters, so look at the runs of digits instead: a 9-digit
     * run that passes the routing checksum, the long run after it (account) and a 3-6 digit run
     * (check number).
     */
    private static void findMicrLine(List<String> lines, Result result) {
        // Bottom line first: that's where MICR is
        for (int i = lines.size() - 1; i >= 0; i--) {
            List<String> runs = new ArrayList<>();
            Matcher m = DIGIT_RUN.matcher(lines.get(i));
            while (m.find()) runs.add(m.group());
            // OCR sometimes glues the runs together: try splitting one long run too
            if (runs.size() == 1 && runs.get(0).length() >= 17) {
                String all = runs.get(0);
                for (int start = 0; start + 9 <= all.length(); start++) {
                    String candidate = all.substring(start, start + 9);
                    if (DatabaseManager.isValidRoutingNumber(candidate) && !candidate.equals(DatabaseManager.BANK_ROUTING_NUMBER)) {
                        runs.clear();
                        if (start > 0) runs.add(all.substring(0, start));
                        runs.add(candidate);
                        if (start + 9 < all.length()) runs.add(all.substring(start + 9));
                        break;
                    }
                }
            }
            for (int r = 0; r < runs.size(); r++) {
                String run = runs.get(r);
                if (run.length() != 9 || !DatabaseManager.isValidRoutingNumber(run)) continue;
                result.routingNumber = run;
                // Account: the next run with 4-17 digits
                for (int k = r + 1; k < runs.size(); k++) {
                    String next = runs.get(k);
                    if (next.length() >= 4 && next.length() <= 17 && result.accountNumber == null) {
                        result.accountNumber = next;
                        continue;
                    }
                    if (result.accountNumber != null && next.length() >= 3 && next.length() <= 6) {
                        result.checkNumber = stripZeros(next);
                        break;
                    }
                }
                // Some checks print the check number before the routing number
                if (result.checkNumber == null && r > 0) {
                    String before = runs.get(r - 1);
                    if (before.length() >= 3 && before.length() <= 6) result.checkNumber = stripZeros(before);
                }
                return;
            }
        }
    }

    /**
     * The amount: prefer numbers written with a "$" (the amount box), then any other number
     * with cents. If there are several, take the biggest (fees or dates are smaller).
     */
    private static Double findAmount(List<String> lines) {
        Double withDollar = null;
        Double without = null;
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.US);
            // Skip the MICR line and dates like 11/13/2026
            if (lower.matches(".*\\d{9}.*") || lower.matches(".*\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}.*")) continue;
            Matcher m = AMOUNT.matcher(line);
            while (m.find()) {
                double value;
                try {
                    value = Double.parseDouble(m.group(2).replace(",", "") + "." + m.group(3));
                } catch (NumberFormatException e) {
                    continue;
                }
                if (value <= 0) continue;
                if (m.group(1) != null) {
                    if (withDollar == null || value > withDollar) withDollar = value;
                } else {
                    if (without == null || value > without) without = value;
                }
            }
        }
        return withDollar != null ? withDollar : without;
    }

    private static String stripZeros(String number) {
        String stripped = number.replaceFirst("^0+(?=\\d)", "");
        return stripped.isEmpty() ? number : stripped;
    }
}
