package fr.cnes.regards.modules.crawler.service.ds;

import fr.cnes.regards.framework.jpa.converters.OffsetDateTimeAttributeConverter;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * External Datasource data.<br/>
 * The aim of this entity is to create a table as if it is an external datasource. It contains a prilary key and a date
 * column to be used by CrawlerService ingestion mechanism.
 *
 * @author oroussel
 */
@Entity
@Table(name = "t_data_3")
@SequenceGenerator(name = "seq3", initialValue = 1, sequenceName = "hibernate_sequence")
public class ExternalData3 {

    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq3")
    private Long id;

    @Column
    @Convert(converter = OffsetDateTimeAttributeConverter.class)
    private OffsetDateTime date;

    public ExternalData3() {
    }

    public ExternalData3(OffsetDateTime date) {
        this.date = date;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public OffsetDateTime getDate() {
        return date;
    }

    public void setDate(OffsetDateTime date) {
        this.date = date;
    }

}
