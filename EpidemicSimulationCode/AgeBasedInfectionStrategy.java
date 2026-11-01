/*
 * Keeps the existing age-based infection rules separate
 * from movement, drawing and treatment.
 */
public class AgeBasedInfectionStrategy implements InfectionStrategy {

    @Override
    public boolean shouldInfect(Healthy person, Virus virus) {
        int chance = virus.getSpreadPercentage();

        if (person.getAge() < 30) {
            person.addExposure();

            // Younger people need at least two close contacts.
            if (person.getExposureCount() < 2) {
                return false;
            }

            chance -= 20;
        } else if (person.getAge() > 50) {
            chance += 25;
        }

        // Preserve the original simulation's probability limits.
        chance = Math.max(5, Math.min(chance, 90));

        return Math.random() * 100 < chance;
    }
}