package com.example.RecommSystem.service;

import com.example.RecommSystem.model.Movie;
import com.example.RecommSystem.model.MovieData;
import com.example.RecommSystem.model.MovieMatch;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class MovieService {

    private final EmbeddingModel embeddingModel;
    private final JsonMapper jsonMapper;

    @Value("${app.enable-embedding-load:false}")
    private boolean enableEmbeddingLoad;

    private final List<Movie> moviesEmbedding = new ArrayList<>();

    public MovieService(EmbeddingModel embeddingModel,
                        JsonMapper jsonMapper) {
        this.embeddingModel = embeddingModel;
        this.jsonMapper = jsonMapper;
    }

    @PostConstruct
    public void initializeMovies() {

        try {

            System.out.println("Embedding Load Enabled = "
                    + enableEmbeddingLoad);

            if (!enableEmbeddingLoad) {
                System.out.println(
                        "Embedding loading disabled. " +
                        "Set app.enable-embedding-load=true to enable."
                );
                return;
            }

            ClassPathResource resource =
                    new ClassPathResource("movies.json");

            if (!resource.exists()) {
                System.out.println(
                        "movies.json not found in src/main/resources"
                );
                return;
            }

            try (InputStream inputStream =
                         resource.getInputStream()) {

                List<MovieData> movieDataList =
                        jsonMapper.readValue(
                                inputStream,
                                new TypeReference<List<MovieData>>() {}
                        );

                for (MovieData movieData : movieDataList) {

                    float[] embedding =
                            embeddingModel.embed(
                                    movieData.getDescription()
                            );

                    Movie movie = new Movie(
                            movieData.getTitle(),
                            movieData.getDescription(),
                            embedding
                    );

                    moviesEmbedding.add(movie);
                }
            }

            System.out.println(
                    moviesEmbedding.size()
                            + " movies loaded successfully."
            );

        } catch (Exception e) {

            System.err.println(
                    "Error loading movie embeddings:"
            );

            e.printStackTrace();
        }
    }

    public List<MovieMatch> search(String query) {

        if (moviesEmbedding.isEmpty()) {
            return Collections.emptyList();
        }

        float[] userEmbedding =
                embeddingModel.embed(query);

        List<MovieMatch> matches = new ArrayList<>();

        for (Movie movie : moviesEmbedding) {

            double similarity =
                    cosineSimilarity(
                            userEmbedding,
                            movie.getEmbedding()
                    );

            matches.add(
                    new MovieMatch(
                            movie.getTitle(),
                            movie.getDescription(),
                            similarity
                    )
            );
        }

        sortBySimilarity(matches);

        return topKMatches(matches, 3);
    }

    public List<MovieMatch> similarMovies(String title) {

        Movie selectedMovie = findMovie(title);

        List<MovieMatch> matches = new ArrayList<>();

        for (Movie movie : moviesEmbedding) {

            if (movie.getTitle().equalsIgnoreCase(title)) {
                continue;
            }

            double similarity =
                    cosineSimilarity(
                            selectedMovie.getEmbedding(),
                            movie.getEmbedding()
                    );

            matches.add(
                    new MovieMatch(
                            movie.getTitle(),
                            movie.getDescription(),
                            similarity
                    )
            );
        }

        sortBySimilarity(matches);

        return topKMatches(matches, 3);
    }

    private Movie findMovie(String title) {

        return moviesEmbedding.stream()
                .filter(m -> m.getTitle()
                        .equalsIgnoreCase(title))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Movie not found: " + title
                        ));
    }

    private double cosineSimilarity(float[] a, float[] b) {

        double dot = 0;
        double normA = 0;
        double normB = 0;

        for (int i = 0; i < a.length; i++) {

            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) {
            return 0;
        }

        return dot /
                (Math.sqrt(normA)
                        * Math.sqrt(normB));
    }

    private void sortBySimilarity(
            List<MovieMatch> matches) {

        matches.sort(
                (a, b) ->
                        Double.compare(
                                b.getMatch(),
                                a.getMatch()
                        )
        );
    }

    private List<MovieMatch> topKMatches(
            List<MovieMatch> matches,
            int limit) {

        return matches.subList(
                0,
                Math.min(limit, matches.size())
        );
    }
}