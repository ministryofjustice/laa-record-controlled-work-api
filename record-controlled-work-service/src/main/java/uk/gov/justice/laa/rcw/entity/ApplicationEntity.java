package uk.gov.justice.laa.rcw.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "APPLICATIONS")

public class ApplicationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ecf;

    private String legalAidBefore;

    private @Nullable String legalAidLast6Months;

    private @Nullable String reasonForYes;

    private String fullName;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateOfBirth;

    private String hasNINumber;

    private @Nullable String niNumber;

    private String haveAHomeAddress;

    private Object address;
}
