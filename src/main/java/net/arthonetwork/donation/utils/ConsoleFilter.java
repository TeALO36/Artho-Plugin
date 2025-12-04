package net.arthonetwork.donation.utils;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

public class ConsoleFilter extends AbstractFilter {

    @Override
    public Filter.Result filter(LogEvent event) {
        return checkMessage(event.getMessage().getFormattedMessage());
    }

    @Override
    public Filter.Result filter(Logger logger, Level level, Marker marker, Message msg, Throwable t) {
        return checkMessage(msg.getFormattedMessage());
    }

    @Override
    public Filter.Result filter(Logger logger, Level level, Marker marker, String msg, Object... params) {
        return checkMessage(msg);
    }

    @Override
    public Filter.Result filter(Logger logger, Level level, Marker marker, Object msg, Throwable t) {
        return checkMessage(msg.toString());
    }

    private Filter.Result checkMessage(String message) {
        if (message == null)
            return Filter.Result.NEUTRAL;
        String lowerMsg = message.toLowerCase();

        // Check for sensitive commands
        if (lowerMsg.contains("issued server command:") &&
                (lowerMsg.contains("/login ") || lowerMsg.contains("/register ")
                        || lowerMsg.contains("/changepassword "))) {
            // Allow masked logs (if any plugin does it correctly)
            if (message.contains("*****")) {
                return Filter.Result.NEUTRAL;
            }
            return Filter.Result.DENY;
        }
        return Filter.Result.NEUTRAL;
    }
}
