package app.cleanyourtv.launcher;

/** Where the sun is for a place and moment (NOAA approximations, good to about a degree). No Android code. */
final class Daylight {
    private Daylight() {}

    /** {altitude, hour angle} in degrees; hour angle is 0 at solar noon, negative in the morning. Longitude east is positive. */
    static double[] sun(double lat, double lon, long millis) {
        double jc = (millis / 86_400_000.0 + 2440587.5 - 2451545.0) / 36525.0;  // Julian centuries since J2000
        double l0 = (280.46646 + jc * (36000.76983 + jc * 0.0003032)) % 360;
        double m = Math.toRadians(357.52911 + jc * (35999.05029 - 0.0001537 * jc));
        double e = 0.016708634 - jc * (0.000042037 + 0.0000001267 * jc);
        double center = Math.sin(m) * (1.914602 - jc * (0.004817 + 0.000014 * jc))
                + Math.sin(2 * m) * (0.019993 - 0.000101 * jc) + Math.sin(3 * m) * 0.000289;
        double omega = Math.toRadians(125.04 - 1934.136 * jc);
        double lambda = Math.toRadians(l0 + center - 0.00569 - 0.00478 * Math.sin(omega));
        double eps = Math.toRadians(23 + (26 + (21.448 - jc * (46.815 + jc * (0.00059 - jc * 0.001813))) / 60) / 60
                + 0.00256 * Math.cos(omega));
        double decl = Math.asin(Math.sin(eps) * Math.sin(lambda));
        double y = Math.pow(Math.tan(eps / 2), 2), l0r = Math.toRadians(l0);
        double eqMinutes = 4 * Math.toDegrees(y * Math.sin(2 * l0r) - 2 * e * Math.sin(m)
                + 4 * e * y * Math.sin(m) * Math.cos(2 * l0r) - 0.5 * y * y * Math.sin(4 * l0r) - 1.25 * e * e * Math.sin(2 * m));
        double utcMinutes = Math.floorMod(millis, 86_400_000L) / 60_000.0;
        double hour = (utcMinutes + eqMinutes + 4 * lon) / 4 - 180;
        hour = ((hour % 360) + 540) % 360 - 180;
        double latR = Math.toRadians(lat), h = Math.toRadians(hour);
        double alt = Math.asin(Math.sin(latR) * Math.sin(decl) + Math.cos(latR) * Math.cos(decl) * Math.cos(h));
        return new double[] {Math.toDegrees(alt), hour};
    }

    /** 0 at night (sun 6 degrees or more below the horizon), 1 by day (6 degrees above), smooth in between. */
    static float light(double altitude) {
        double t = Math.max(0, Math.min(1, (altitude + 6) / 12));
        return (float) (t * t * (3 - 2 * t));
    }

    /** {latitude, longitude} of a time zone's main city, from the text of IANA zone.tab; null if it isn't listed. */
    static double[] coords(String zoneTab, String zone) {
        for (String line : zoneTab.split("\n")) {
            String[] f = line.split("\t");
            if (!line.startsWith("#") && f.length >= 3 && f[2].trim().equals(zone)) return iso6709(f[1]);
        }
        return null;
    }

    /** "+4730+01905" or "+404251-0740023": degrees and minutes, optionally seconds. */
    static double[] iso6709(String s) {
        int split = Math.max(s.lastIndexOf('+'), s.lastIndexOf('-'));
        return new double[] {degrees(s.substring(0, split), 2), degrees(s.substring(split), 3)};
    }

    private static double degrees(String s, int degreeDigits) {
        String d = s.substring(1);
        double v = Integer.parseInt(d.substring(0, degreeDigits)) + Integer.parseInt(d.substring(degreeDigits, degreeDigits + 2)) / 60.0;
        if (d.length() > degreeDigits + 2) v += Integer.parseInt(d.substring(degreeDigits + 2)) / 3600.0;
        return s.charAt(0) == '-' ? -v : v;
    }
}
