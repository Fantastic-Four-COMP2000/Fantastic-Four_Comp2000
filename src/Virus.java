public class Virus {

    private String virusName;
    private int spreadPercentage;

    public Virus(String virusName, int spreadPercentage) {

        // Reject missing or blank virus names.
        if (virusName == null || virusName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Virus name cannot be empty."
            );
        }

        // A percentage must be between 0 and 100, inclusive.
        if (spreadPercentage < 0 || spreadPercentage > 100) {
            throw new IllegalArgumentException(
                    "Spread percentage must be between 0 and 100."
            );
        }

        this.virusName = virusName.trim();
        this.spreadPercentage = spreadPercentage;
    }

    public String getVirusName() {
        return virusName;
    }

    public int getSpreadPercentage() {
        return spreadPercentage;
    }

    @Override
    public String toString() {
        return virusName + " - " + spreadPercentage + "%";
    }
}