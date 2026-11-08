// Strategy pattern: calculates how a base infection chance should be
// adjusted for a specific healthy person. Different age groups plug in
// a different strategy (see SimulationPanel.strategyFor()).
@FunctionalInterface
public interface InfectionStrategy {

    // Returns the adjusted infection chance (0-100).
    // A negative return value means this person isn't eligible to be
    // infected on this check at all (used by the under-30 strategy,
    // which requires repeated exposure before infection is possible).
    int adjustChance(int baseChance, Healthy person);
}