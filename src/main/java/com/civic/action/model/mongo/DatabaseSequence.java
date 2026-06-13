package com.civic.action.model.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;

@Document(collection = "database_sequences")
@Data
public class DatabaseSequence {
    @Id
    private String id; // Represents the sequence name (e.g. "issue_sequence")
    private long seq;
}
