/*
 * Different infection rules use the same interface.
 * SimulationPanel can use a rule without knowing its implementation.
 *
 * One abstract method means this interface also supports lambdas.
 */
@FunctionalInterface
public interface InfectionStrategy {

    boolean shouldInfect(Healthy person, Virus virus);
}