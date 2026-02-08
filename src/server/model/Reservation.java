package server.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.format.DateTimeParseException;

public record Reservation(String customerID, String startDate, String endDate) {

    // ddMMyyyy
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT);

    private static LocalDate parse(String ddMMyyyy) throws DateTimeParseException {
        return LocalDate.parse(ddMMyyyy.trim(), FMT);
    }

    //check reservation [startDate, endDate] with [newStart, newEnd]
    public boolean overlaps(String newStart, String newEnd) {
        LocalDate aStart = parse(startDate);
        LocalDate aEnd   = parse(endDate);
        LocalDate bStart = parse(newStart);
        LocalDate bEnd   = parse(newEnd);

        return !(aEnd.isBefore(bStart) || bEnd.isBefore(aStart));
    }

    //for server
    public static boolean isValidRange(String start, String end) {
        try {
            LocalDate s = parse(start.trim());
            LocalDate e = parse(end.trim());
            return !e.isBefore(s);
        } catch (DateTimeParseException ex) {
            ex.printStackTrace();
            return false;
        }

    }

}
