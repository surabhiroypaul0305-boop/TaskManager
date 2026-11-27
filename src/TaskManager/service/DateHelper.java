package TaskManager.service;

import java.time.LocalDate;

/**
 * INSTRUCTOR-PROVIDED (Step 3, optional - learners may write their own).
 *
 * Date handling was not covered in class, so this is supplied. It checks the month and
 * day by hand (including leap years) instead of leaning on a library, so students can
 * read and understand every line.
 *
 * When you copy this into your own project, change the package line to: package service;
 */
public final class DateHelper {

    private DateHelper() {
    }

    /** Today as YYYY-MM-DD. Pass this to TaskParser.parse(text, today). */
    public static String today() {
        return LocalDate.now().toString();
    }

    /** True only for a real calendar date written exactly as YYYY-MM-DD. */
    public static boolean isValidDate(String value) {
        if (value == null || value.length() != 10) {
            return false;
        }
        if (value.charAt(4) != '-' || value.charAt(7) != '-') {
            return false;
        }
        String yearText = value.substring(0, 4);
        String monthText = value.substring(5, 7);
        String dayText = value.substring(8, 10);
        if (!allDigits(yearText) || !allDigits(monthText) || !allDigits(dayText)) {
            return false;
        }
        int year = Integer.parseInt(yearText);
        int month = Integer.parseInt(monthText);
        int day = Integer.parseInt(dayText);
        if (month < 1 || month > 12) {
            return false;
        }
        return day >= 1 && day <= daysInMonth(year, month);
    }

    public static boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
    }

    public static int daysInMonth(int year, int month) {
        switch (month) {
            case 2:
                return isLeapYear(year) ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    private static boolean allDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {   // plain 0-9 only (Character.isDigit also accepts other alphabets)
                return false;
            }
        }
        return true;
    }
}
