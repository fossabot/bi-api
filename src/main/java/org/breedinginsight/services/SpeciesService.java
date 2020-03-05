package org.breedinginsight.services;

import lombok.extern.slf4j.Slf4j;
import org.breedinginsight.api.model.v1.request.SpeciesRequest;
import org.breedinginsight.dao.db.tables.pojos.SpeciesEntity;
import org.breedinginsight.daos.ProgramDao;
import org.breedinginsight.daos.SpeciesDao;
import org.breedinginsight.model.Species;
import org.breedinginsight.services.exceptions.DoesNotExistException;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class SpeciesService {

    @Inject
    private SpeciesDao dao;

    public Species getById(SpeciesRequest speciesRequest) throws DoesNotExistException{

        SpeciesEntity speciesEntity = dao.fetchOneById(speciesRequest.getId());

        if (speciesEntity == null){
            throw new DoesNotExistException("Species does not exist");
        }

        Species species = new Species(speciesEntity);
        return species;
    }
}