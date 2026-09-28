package com.stockflow.order;

import com.stockflow.common.exception.ApiExceptions.InvalidStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import static com.stockflow.order.Order.Status.*;

/**
 * Single source of truth for legal order-status transitions. Every status change in the system
 * goes through assertLegal() so an "PROCESSING -> PENDING" style bug becomes a thrown exception
 * instead of a silently-corrupt order record.
 */
@Component
public class OrderStateMachine {

    private static final Map<Order.Status, EnumSet<Order.Status>> TRANSITIONS = new EnumMap<>(Order.Status.class);
    static {
        TRANSITIONS.put(PENDING, EnumSet.of(PAYMENT_PROCESSING, CANCELLED));
        TRANSITIONS.put(PAYMENT_PROCESSING, EnumSet.of(CONFIRMED, CANCELLED));
        TRANSITIONS.put(CONFIRMED, EnumSet.of(PROCESSING, CANCELLED));
        TRANSITIONS.put(PROCESSING, EnumSet.of(SHIPPED, CANCELLED));
        TRANSITIONS.put(SHIPPED, EnumSet.of(DELIVERED));
        TRANSITIONS.put(DELIVERED, EnumSet.noneOf(Order.Status.class));
        TRANSITIONS.put(CANCELLED, EnumSet.noneOf(Order.Status.class));
    }

    public void assertLegal(Order.Status from, Order.Status to) {
        if (!TRANSITIONS.getOrDefault(from, EnumSet.noneOf(Order.Status.class)).contains(to)) {
            throw new InvalidStateTransitionException("Cannot transition order from " + from + " to " + to);
        }
    }
}
