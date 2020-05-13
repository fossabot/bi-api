package org.breedinginsight.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.CountryEntity;
import org.jooq.Record;

import static org.breedinginsight.dao.db.Tables.COUNTRY;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(value = { "alpha_2Code", "alpha_3Code" })
public class Country extends CountryEntity {

    // these getters/setters are to get correct camel case to/from json
    // jooq generator puts weird underscores in naming we have to deal with
    public String getAlpha2Code() {
        return super.getAlpha_2Code();
    }

    public String getAlpha3Code() {
        return super.getAlpha_3Code();
    }

    public void setAlpha2Code(String alpha2Code) {
        super.setAlpha_2Code(alpha2Code);
    }

    public void setAlpha3Code(String alpha3Code) {
        super.setAlpha_3Code(alpha3Code);
    }

    public Country(CountryEntity countryEntity) {
        this.setId(countryEntity.getId());
        this.setName(countryEntity.getName());
        this.setAlpha_2Code(countryEntity.getAlpha_2Code());
        this.setAlpha_3Code(countryEntity.getAlpha_3Code());
    }

    public static Country parseSQLRecord(Record record) {
        return Country.builder()
                .id(record.getValue(COUNTRY.ID))
                .name(record.getValue(COUNTRY.NAME))
                .alpha_2Code(record.getValue(COUNTRY.ALPHA_2_CODE))
                .alpha_3Code(record.getValue(COUNTRY.ALPHA_3_CODE))
                .build();
    }
}
