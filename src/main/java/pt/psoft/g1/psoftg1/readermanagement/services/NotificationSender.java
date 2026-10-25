package pt.psoft.g1.psoftg1.readermanagement.services;

/**
 * Strategy interface (driven port) representing a reader notification delivery
 * channel.
 * Implemented by concrete notification adapters (e.g., Email, SMS, Webhook)
 * and composite dispatchers.
 */
public interface NotificationSender {

    void send(NotificationRequest request);

    String getChannelName();

}
