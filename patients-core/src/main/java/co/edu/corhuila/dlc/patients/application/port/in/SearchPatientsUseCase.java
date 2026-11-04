package co.edu.corhuila.dlc.patients.application.port.in;

import co.edu.corhuila.dlc.patients.application.model.PatientSearchPage;

public interface SearchPatientsUseCase {
    PatientSearchPage search(Query query);
    record Query(Integer page, Integer limit, String search, String status) { }
}
