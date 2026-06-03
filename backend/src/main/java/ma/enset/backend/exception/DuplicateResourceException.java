package ma.enset.backend.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String resource, String field, Object value) {
        super(resource + " already exists with " + field + ": " + value,
                HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
    }
}
