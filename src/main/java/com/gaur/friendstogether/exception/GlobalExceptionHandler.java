package com.gaur.friendstogether.exception;
import com.gaur.friendstogether.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * GlobalExceptionHandler: Saari errors ko ek hi jagah se control karne ke liye.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Hamari banayi hui ResourceNotFoundException handle karne ke liye
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .build();
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    // 2. Hamari banayi hui BusinessLogicException handle karne ke liye (e.g. Room Full)
    @ExceptionHandler(BusinessLogicException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessLogicException ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(ex.getMessage())
                .build();
        return new ResponseEntity<>(response, ex.getStatus());
    }

    // 3. Koi bhi anjaan Runtime error ke liye
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message("Logic Error: " + ex.getMessage())
                .build();
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // 4. Bilkul unexpected error (Server down etc.) ke liye
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAll(Exception ex) {
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message("Server Error: An unexpected error occurred.")
                .build();
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
