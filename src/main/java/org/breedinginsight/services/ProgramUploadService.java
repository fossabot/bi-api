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
package org.breedinginsight.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.CompletedFileUpload;
import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.auth.AuthenticatedUser;
import org.breedinginsight.dao.db.enums.DataType;
import org.breedinginsight.dao.db.enums.UploadType;
import org.breedinginsight.daos.ProgramUploadDAO;
import org.breedinginsight.model.Method;
import org.breedinginsight.model.ProgramUpload;
import org.breedinginsight.model.Scale;
import org.breedinginsight.services.exceptions.DoesNotExistException;
import org.breedinginsight.services.exceptions.UnprocessableEntityException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.breedinginsight.dao.db.tables.pojos.BatchUploadEntity;
import org.breedinginsight.model.Trait;
import org.breedinginsight.services.parsers.ParsingException;
import org.breedinginsight.services.parsers.trait.TraitFileParser;
import org.jooq.JSONB;

import java.io.IOException;
import java.util.*;

@Slf4j
@Singleton
public class ProgramUploadService {

    @Value("${trait.upload.mime.types}")
    private Set<String> mimeTypes;

    @Inject
    private ProgramUploadDAO programUploadDao;
    @Inject
    private ProgramService programService;
    @Inject
    private ProgramUserService programUserService;
    @Inject
    private TraitFileParser parser;

    public ProgramUpload updateTraitUpload(UUID programId, CompletedFileUpload file, AuthenticatedUser actingUser)
            throws UnprocessableEntityException, DoesNotExistException {

        if (!programService.exists(programId))
        {
            throw new DoesNotExistException("Program id does not exist");
        }

        programUserService.getProgramUserbyId(programId, actingUser.getId())
                .orElseThrow(() -> new DoesNotExistException("user not in program"));

        Optional<MediaType> type = file.getContentType();
        MediaType mediaType = type.orElseThrow(() -> new UnprocessableEntityException("File upload must have MediaType"));

        log.info(mediaType.getName());
        log.info(mediaType.getType());
        log.info(mediaType.getExtension());
        log.info(file.getFilename());

        if (!mimeTypes.contains(mediaType.getName())) {
            // TODO: 415
            throw new UnprocessableEntityException("Unsupported mime type");
        }

        List<Trait> traits = new ArrayList<>();

        // TODO: parse based on file extension and mimeType

        try {
            traits = parser.parseCsv(file.getInputStream());
        } catch(IOException e) {
            log.error(e.getMessage());
        } catch(ParsingException e) {
            log.error(e.getMessage());
        }

        for (Trait trait : traits) {
            checkRequiredTraitFields(trait);
            checkTraitDataConsistency(trait);
        }

        String json = null;

        ObjectMapper objMapper = new ObjectMapper();
        try {
            json = objMapper.writeValueAsString(traits);
        } catch(JsonProcessingException e) {
            log.error(e.getMessage());
        }

        // delete any existing records for traits since we only want to allow one at a time
        // if there is some failure in writing the new, the old will be wiped out but that's ok
        // because by making the PUT call the client already expected an overwrite
        programUploadDao.deleteUploads(programId, actingUser.getId(), UploadType.TRAIT);

        // do not autopopulate fields, that will be done on actual trait creation
        BatchUploadEntity uploadEntity = BatchUploadEntity.builder()
                .type(UploadType.TRAIT)
                .programId(programId)
                .userId(actingUser.getId())
                .data(JSONB.valueOf(json))
                .createdBy(actingUser.getId())
                .updatedBy(actingUser.getId())
                .build();

        // Insert and update
        programUploadDao.insert(uploadEntity);
        ProgramUpload upload = new ProgramUpload(programUploadDao.fetchById(uploadEntity.getId()).get(0));

        return upload;
    }

    private void checkRequiredTraitFields(Trait trait) throws UnprocessableEntityException {
        if (trait.getTraitName() == null) {
            throw new UnprocessableEntityException("Missing trait name");
        }
        if (trait.getDescription() == null) {
            throw new UnprocessableEntityException("Missing trait description");
        }
        if (trait.getProgramObservationLevel() == null || trait.getProgramObservationLevel().getName() == null) {
            throw new UnprocessableEntityException("Missing trait level");
        }
        if (trait.getMethod() == null || trait.getMethod().getMethodName() == null) {
            throw new UnprocessableEntityException("Missing method name");
        }
        if (trait.getMethod() == null || trait.getMethod().getDescription() == null) {
            throw new UnprocessableEntityException("Missing method description");
        }
        if (trait.getMethod() == null || trait.getMethod().getMethodClass() == null) {
            throw new UnprocessableEntityException("Missing method class");
        }
        if (trait.getScale() == null || trait.getScale().getScaleName() == null) {
            throw new UnprocessableEntityException("Missing scale name");
        }
        if (trait.getScale() == null || trait.getScale().getDataType() == null) {
            throw new UnprocessableEntityException("Missing scale type");
        }
    }

    private void checkTraitDataConsistency(Trait trait) throws UnprocessableEntityException {

        Method method = trait.getMethod();
        Scale scale = trait.getScale();

        if (method != null && method.getMethodClass().equals(Method.COMPUTATION_TYPE)) {
            if (method.getFormula() == null) {
                throw new UnprocessableEntityException("Missing formula for Computation method");
            }
        }

        if (scale != null && scale.getDataType() == DataType.ORDINAL) {
            if (scale.getCategories() == null || scale.getCategories().isEmpty()) {
                throw new UnprocessableEntityException("Missing categories for Ordinal scale");
            }
        }
    }


    public Optional<ProgramUpload> getTraitUpload(UUID programId, AuthenticatedUser actingUser) {

        List<ProgramUpload> uploads = programUploadDao.getUploads(programId, actingUser.getId(), UploadType.TRAIT);

        if (uploads.isEmpty()) {
            return Optional.empty();
        } else if (uploads.size() > 1) {
            throw new IllegalStateException("Trait upload had more than 1 record, only 1 allowed");
        }

        return Optional.of(uploads.get(0));
    }


    public void deleteTraitUpload(UUID programId, AuthenticatedUser actingUser) throws DoesNotExistException {

        if (!programService.exists(programId))
        {
            throw new DoesNotExistException("Program id does not exist");
        }

        programUserService.getProgramUserbyId(programId, actingUser.getId())
                .orElseThrow(() -> new DoesNotExistException("user not in program"));

        programUploadDao.deleteUploads(programId, actingUser.getId(), UploadType.TRAIT);

    }


}
