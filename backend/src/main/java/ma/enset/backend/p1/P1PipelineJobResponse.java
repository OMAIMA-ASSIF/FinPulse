package ma.enset.backend.p1;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class P1PipelineJobResponse {

    @JsonProperty("job_id")
    private String jobId;

    private String status;

    @JsonProperty("status_url")
    private String statusUrl;
}
