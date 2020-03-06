package org.breedinginsight.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.ProgramUserRoleEntity;
import org.breedinginsight.dao.db.tables.pojos.RoleEntity;

import java.util.List;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
public class ProgramUser {

    private User user;
    private List<RoleEntity> roles;

}