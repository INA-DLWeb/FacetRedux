package fr.ina.dlweb.proprioception.models;

/**
 * Date: 27/11/12
 * Time: 15:26
 *
 * @author drapin
 */
abstract class PigTask {
    protected Failure failure = null;

    protected PigTask() {
    }

    protected PigTask(Failure failure) {
        this.failure = failure;
    }

    public Failure getFailure() {
        return failure;
    }
}
