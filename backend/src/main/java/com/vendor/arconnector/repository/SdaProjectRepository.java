package com.vendor.arconnector.repository;

import com.vendor.arconnector.domain.SdaProject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SdaProjectRepository extends JpaRepository<SdaProject, String> {

    /**
     * Búsqueda para el selector del panel: por id o descripción, sin los dados
     * de baja. Los filtros vacíos ('') no se aplican.
     *
     * @param q            texto a buscar en el id o la descripción ('' = todos)
     * @param evolutionary 'true' = solo SDA de evolutivo, 'false' = solo los que no lo son, '' = todos
     */
    @Query("""
            select p from SdaProject p
            where p.disabledDate is null
              and (:evolutionary = ''
                   or (:evolutionary = 'true' and p.evolutionarySda = true)
                   or (:evolutionary = 'false' and p.evolutionarySda = false))
              and (:q = ''
                   or lower(p.sdaProjectId) like concat('%', lower(:q), '%')
                   or lower(p.description) like concat('%', lower(:q), '%'))
            order by p.sdaProjectId
            """)
    List<SdaProject> search(@Param("q") String q, @Param("evolutionary") String evolutionary, Pageable pageable);
}
