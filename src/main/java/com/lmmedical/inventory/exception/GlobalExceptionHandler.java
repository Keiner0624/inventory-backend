package com.lmmedical.inventory.exception;

import com.lmmedical.inventory.domain.StockInsuficienteException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleInvalidJson(HttpMessageNotReadableException exception, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
        "El cuerpo JSON es inválido o contiene valores de un tipo incorrecto");
    problem.setTitle("Error de formato");
    problem.setProperty("ruta", request.getRequestURI());
    return ResponseEntity.badRequest().body(problem);
  }

  @ExceptionHandler(PropertyReferenceException.class)
  public ResponseEntity<ProblemDetail> handleInvalidSort(PropertyReferenceException exception, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "El campo de ordenamiento no es válido");
    problem.setTitle("Error de ordenamiento");
    problem.setProperty("ruta", request.getRequestURI());
    return ResponseEntity.badRequest().body(problem);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ProblemDetail> handleMediaType(HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "Envía el cuerpo como JSON y usa Content-Type: application/json");
    problem.setTitle("Tipo de contenido no admitido");
    problem.setProperty("ruta", request.getRequestURI());
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(problem);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException exception, HttpServletRequest request){
    HttpStatusCode statusCode = exception.getStatusCode();

    String mensaje = exception.getReason() != null ? exception.getReason() : "No se pudo procesar la solicitud";
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(statusCode, mensaje);

    problem.setTitle("Error en la solicitud");
    problem.setProperty("ruta", request.getRequestURI());

    return ResponseEntity.status(statusCode).body(problem);
  }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request){
    Map<String, String> errors = new HashMap<>();
    exception.getBindingResult().getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()
    != null ? error.getDefaultMessage() : "Valor válido"));

    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Los datos enviados no son válidos");
    problem.setTitle("Error de validación");
    problem.setProperty("campos", errors);
    problem.setProperty("ruta", request.getRequestURI());

    return ResponseEntity.badRequest().body(problem);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleIllegalState(IllegalArgumentException exception, HttpServletRequest request){
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    problem.setTitle("Conflicto de operación");
    problem.setProperty("ruta", request.getRequestURI());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ProblemDetail> handleDatabaseConflict(DataIntegrityViolationException exception, HttpServletRequest request){
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "La operación viola una restricción de la base de datos");
    problem.setTitle("Conflicto de integridad");
    problem.setProperty("ruta", request.getRequestURI());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
  }

  @ExceptionHandler(StockInsuficienteException.class)
  public ProblemDetail manejarStockInsuficiente(StockInsuficienteException exception){
    ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    problema.setTitle("Stock Insuficiente");
    return problema;
  }

}
