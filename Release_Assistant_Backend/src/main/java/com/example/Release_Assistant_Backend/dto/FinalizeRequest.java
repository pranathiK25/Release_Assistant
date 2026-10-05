package com.example.Release_Assistant_Backend.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FinalizeRequest {
    @NotBlank(message = "Reviewer name is required.")
    @Size(max = 100, message = "Reviewer name must be under 100 characters.")
    private String reviewer;
}
