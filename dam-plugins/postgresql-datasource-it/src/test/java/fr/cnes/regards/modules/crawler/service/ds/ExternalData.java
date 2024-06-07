package fr.cnes.regards.modules.crawler.service.ds;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * External Datasource data.<br/>
 * The aim of this entity is to create a table as if it is an external datasource. It contains a prilary key and a date
 * column to be used by CrawlerService ingestion mechanism.
 *
 * @author oroussel
 */
@Entity
@Table(name = "t_data")
@SequenceGenerator(name = "seq", initialValue = 1, sequenceName = "hibernate_sequence")
public class ExternalData {

    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq")
    private Long id;

    @Column
    private LocalDate date;

    public ExternalData() {
    }

    public ExternalData(LocalDate date) {
        this.date = date;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

}
