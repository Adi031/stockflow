package com.stockflow.order;

import com.stockflow.common.exception.ApiExceptions.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderStateMachineTest {

    private final OrderStateMachine sm = new OrderStateMachine();

    @Test
    void allowsTheHappyPath() {
        assertDoesNotThrow(() -> sm.assertLegal(Order.Status.PENDING, Order.Status.PAYMENT_PROCESSING));
        assertDoesNotThrow(() -> sm.assertLegal(Order.Status.PAYMENT_PROCESSING, Order.Status.CONFIRMED));
        assertDoesNotThrow(() -> sm.assertLegal(Order.Status.CONFIRMED, Order.Status.PROCESSING));
        assertDoesNotThrow(() -> sm.assertLegal(Order.Status.PROCESSING, Order.Status.SHIPPED));
        assertDoesNotThrow(() -> sm.assertLegal(Order.Status.SHIPPED, Order.Status.DELIVERED));
    }

    @Test
    void rejectsSkippingStates() {
        assertThrows(InvalidStateTransitionException.class,
                () -> sm.assertLegal(Order.Status.PENDING, Order.Status.SHIPPED));
    }

    @Test
    void rejectsTransitionsOutOfTerminalStates() {
        assertThrows(InvalidStateTransitionException.class,
                () -> sm.assertLegal(Order.Status.DELIVERED, Order.Status.CANCELLED));
        assertThrows(InvalidStateTransitionException.class,
                () -> sm.assertLegal(Order.Status.CANCELLED, Order.Status.PENDING));
    }

    @Test
    void allowsCancellationFromEveryPreShippingState() {
        assertDoesNotThrow(() -> sm.assertLegal(Order.Status.PENDING, Order.Status.CANCELLED));
        assertDoesNotThrow(() -> sm.assertLegal(Order.Status.PAYMENT_PROCESSING, Order.Status.CANCELLED));
        assertDoesNotThrow(() -> sm.assertLegal(Order.Status.CONFIRMED, Order.Status.CANCELLED));
        assertDoesNotThrow(() -> sm.assertLegal(Order.Status.PROCESSING, Order.Status.CANCELLED));
    }
}
