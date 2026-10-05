package com.example.Release_Assistant_Backend.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Either field may be null. Editing text marks the statement EDITED unless a status is also sent. */
@Data
public class ReviewRequest {
    @Size(max = 2000, message = "Statement text must be under 2000 characters.")
    private String text;

    @Pattern(regexp = "PENDING|ACCEPTED|REJECTED|EDITED", message = "Status must be PENDING, ACCEPTED, REJECTED or EDITED.")
    private String status;
}
