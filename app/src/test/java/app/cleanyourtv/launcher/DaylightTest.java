package app.cleanyourtv.launcher;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;

import org.junit.Test;

public class DaylightTest {
    private static final double LAT = 47.5, LON = 19.083;  // Budapest

    private static double altitude(String utc) { return Daylight.sun(LAT, LON, Instant.parse(utc).toEpochMilli())[0]; }

    @Test public void noonAltitudeFollowsTheSeasons() {
        assertEquals(90 - LAT + 23.44, altitude("2026-06-21T10:46:00Z"), 1.0);  // solar noon, summer solstice
        assertEquals(90 - LAT - 23.44, altitude("2026-12-21T10:47:00Z"), 1.0);  // solar noon, winter solstice
    }

    @Test public void midnightIsBelowTheHorizonAndMorningIsBeforeNoon() {
        assertTrue(altitude("2026-06-21T22:46:00Z") < -15);
        double[] morning = Daylight.sun(LAT, LON, Instant.parse("2026-06-21T05:00:00Z").toEpochMilli());
        assertTrue(morning[0] > 0 && morning[1] < -60);
    }

    @Test public void lightIsDarkAtNightFullByDayHalfAtTheHorizon() {
        assertEquals(0f, Daylight.light(-19), 0f);
        assertEquals(1f, Daylight.light(66), 0f);
        assertEquals(0.5f, Daylight.light(0), 1e-6f);
    }

    @Test public void zoneTabCoordinates() {
        String tab = "# comment\nHU\t+4730+01905\tEurope/Budapest\nUS\t+404251-0740023\tAmerica/New_York\tEastern\n";
        assertArrayEquals(new double[] {47.5, 19 + 5 / 60.0}, Daylight.coords(tab, "Europe/Budapest"), 1e-9);
        assertArrayEquals(new double[] {40 + 42 / 60.0 + 51 / 3600.0, -(74 + 23 / 3600.0)}, Daylight.coords(tab, "America/New_York"), 1e-9);
        assertArrayEquals(new double[] {-33 - 52 / 60.0, 151 + 13 / 60.0}, Daylight.iso6709("-3352+15113"), 1e-9);
        assertNull(Daylight.coords(tab, "Etc/UTC"));
    }
}
