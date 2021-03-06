/*
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.breedinginsight.model;

import com.fasterxml.jackson.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.breedinginsight.dao.db.tables.pojos.ProgramUserRoleEntity;
import org.jooq.Record;

import java.util.ArrayList;
import java.util.List;

import static org.breedinginsight.dao.db.Tables.PROGRAM_USER_ROLE;

@Getter
@Setter
@Accessors(chain=true)
@ToString
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(value = { "createdBy", "updatedBy", "programId", "userId", "id" })
public class ProgramUser extends ProgramUserRoleEntity {

    private User createdByUser;
    private User updatedByUser;

    @JsonIgnoreProperties(value={"createdAt", "createdByUser", "updatedAt", "updatedByUser", "active", "species"})
    private Program program;

    @JsonIgnoreProperties(value={"programRoles", "systemRoles"})
    private User user;
    private List<Role> roles;

    public static ProgramUser parseSQLRecord(Record record){
        // Generate our program record
        ProgramUser programUser = ProgramUser.builder()
                .id(record.getValue(PROGRAM_USER_ROLE.ID))
                .roles(new ArrayList<>())
                .programId(record.getValue(PROGRAM_USER_ROLE.PROGRAM_ID))
                .userId(record.getValue(PROGRAM_USER_ROLE.USER_ID))
                .createdAt(record.getValue(PROGRAM_USER_ROLE.CREATED_AT))
                .updatedAt(record.getValue(PROGRAM_USER_ROLE.UPDATED_AT))
                .createdBy(record.getValue(PROGRAM_USER_ROLE.CREATED_BY))
                .updatedBy(record.getValue(PROGRAM_USER_ROLE.UPDATED_BY))
                .active(record.getValue(PROGRAM_USER_ROLE.ACTIVE))
                .build();

        return programUser;
    }

    public void addRole(Role role) {
        roles.add(role);
    }
}
