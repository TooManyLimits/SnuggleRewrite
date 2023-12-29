package errors

/**
 * Represents runtime errors thrown from Snuggle, as opposed
 * to compilation errors. Uses a *checked* Java exception, but
 * this doesn't force our bytecode to handle the exceptions,
 * only enclosing Java code needs to handle them.
 */
class SnuggleException(message: String): Exception(message)