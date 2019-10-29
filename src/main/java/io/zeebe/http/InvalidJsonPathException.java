package io.zeebe.http;

public class InvalidJsonPathException extends Exception {

	public InvalidJsonPathException(String message){
		super(message);
	}
}
