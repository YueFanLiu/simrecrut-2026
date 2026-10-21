package fr.isep.simrecrut.service.identity;

public class AccountConflictException extends RuntimeException {

    public AccountConflictException() {
        super("The account conflicts with an existing record.");
    }
}
