package io.datakernel.exception;

@SuppressWarnings("ThrowableInstanceNeverThrown")
public class ToDoException extends Exception {

	public ToDoException() {
		super();
	}

	public ToDoException(String message, Throwable cause) {
		super(message, cause);
	}

	public ToDoException(String s) {
		super(s);
	}

	public ToDoException(Throwable cause) {
		super(cause);
	}
}
