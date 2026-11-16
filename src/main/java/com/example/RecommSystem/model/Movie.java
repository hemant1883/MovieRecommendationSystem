package com.example.RecommSystem.model;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Getter;

@Getter @Setter  @AllArgsConstructor @NoArgsConstructor 
public class Movie {
    private String title;
    private String description;
    private float[] embedding;
}
