package com.bomerp.config.empresa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguracionEmpresaServiceTest {

    private FakeConfiguracionEmpresaRepository repository;
    private EmpresaProperties props;
    private ConfiguracionEmpresaService service;

    @BeforeEach
    void setUp() {
        repository = new FakeConfiguracionEmpresaRepository();
        props = new EmpresaProperties("JD REFRIGERACIÓN S.A.C.", "20521632268", "Lima", null,
                "912345678", "contacto@jd.pe", "jd.pe",
                "193-12345678-0-12", "002-193-001234567890-12", "00-012-345678");
        service = new ConfiguracionEmpresaService(repository, props);
    }

    @Test
    void siNoHayFilaEnBdDevuelveValoresDeProperties() {
        ConfiguracionEmpresaDTO conf = service.obtener();

        assertThat(conf.whatsapp()).isEqualTo("912345678");
        assertThat(conf.cuentaBcp()).isEqualTo("193-12345678-0-12");
        assertThat(conf.cciBcp()).isEqualTo("002-193-001234567890-12");
        assertThat(conf.cuentaNacion()).isEqualTo("00-012-345678");
        assertThat(conf.actualizadoPor()).isNull();
    }

    @Test
    void guardarActualizaDatosYGuardaEnRepositorio() {
        ConfiguracionEmpresaDTO nuevo = new ConfiguracionEmpresaDTO(
                "987654321", "191-99999999-0-99", "002-191-009999999999-99", "00-999-999999", null, null
        );

        ConfiguracionEmpresaDTO resultado = service.cambiar(nuevo, "lisandro");

        assertThat(resultado.whatsapp()).isEqualTo("987654321");
        assertThat(resultado.cuentaBcp()).isEqualTo("191-99999999-0-99");
        assertThat(resultado.actualizadoPor()).isEqualTo("lisandro");
        assertThat(resultado.fechaActualizacion()).isNotNull();

        assertThat(repository.saved).isNotNull();
        assertThat(repository.saved.getWhatsapp()).isEqualTo("987654321");
        assertThat(repository.saved.getCuentaBcp()).isEqualTo("191-99999999-0-99");
    }

    @Test
    void borrarDatosLosMantieneVaciosAunqueExistanProperties() {
        service.cambiar(new ConfiguracionEmpresaDTO(" ", null, null, null, null, null), "admin");
        ConfiguracionEmpresaDTO resultado = service.obtener();
        assertThat(resultado.whatsapp()).isNull();
        assertThat(resultado.cuentaBcp()).isNull();
        assertThat(resultado.cciBcp()).isNull();
        assertThat(resultado.cuentaNacion()).isNull();
    }

    @Test
    void sinConfiguracionNoInventaCuentasNiTelefono() {
        service = new ConfiguracionEmpresaService(repository, new EmpresaProperties("JD", "123", "Lima", null));
        assertThat(service.obtener().cuentaBcp()).isNull();
        assertThat(service.obtener().cuentaNacion()).isNull();
        assertThat(service.obtener().whatsapp()).isNull();
    }

    static class FakeConfiguracionEmpresaRepository implements ConfiguracionEmpresaRepository {
        ConfiguracionEmpresa saved = null;

        @Override
        public Optional<ConfiguracionEmpresa> findById(Long id) {
            return Optional.ofNullable(saved);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <S extends ConfiguracionEmpresa> S save(S entity) {
            this.saved = entity;
            return entity;
        }

        @Override public void flush() {}
        @Override public <S extends ConfiguracionEmpresa> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends ConfiguracionEmpresa> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<ConfiguracionEmpresa> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) {}
        @Override public void deleteAllInBatch() {}
        @Override public ConfiguracionEmpresa getOne(Long id) { return null; }
        @Override public ConfiguracionEmpresa getById(Long id) { return null; }
        @Override public ConfiguracionEmpresa getReferenceById(Long id) { return null; }
        @Override public <S extends ConfiguracionEmpresa> List<S> findAll(Example<S> example) { return null; }
        @Override public <S extends ConfiguracionEmpresa> List<S> findAll(Example<S> example, Sort sort) { return null; }
        @Override public <S extends ConfiguracionEmpresa> List<S> saveAll(Iterable<S> entities) { return null; }
        @Override public List<ConfiguracionEmpresa> findAll() { return null; }
        @Override public List<ConfiguracionEmpresa> findAllById(Iterable<Long> ids) { return null; }
        @Override public boolean existsById(Long id) { return saved != null; }
        @Override public long count() { return saved == null ? 0 : 1; }
        @Override public void deleteById(Long id) { saved = null; }
        @Override public void delete(ConfiguracionEmpresa entity) { saved = null; }
        @Override public void deleteAllById(Iterable<? extends Long> ids) {}
        @Override public void deleteAll(Iterable<? extends ConfiguracionEmpresa> entities) {}
        @Override public void deleteAll() { saved = null; }
        @Override public List<ConfiguracionEmpresa> findAll(Sort sort) { return null; }
        @Override public Page<ConfiguracionEmpresa> findAll(Pageable pageable) { return null; }
        @Override public <S extends ConfiguracionEmpresa> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends ConfiguracionEmpresa> Page<S> findAll(Example<S> example, Pageable pageable) { return null; }
        @Override public <S extends ConfiguracionEmpresa> long count(Example<S> example) { return 0; }
        @Override public <S extends ConfiguracionEmpresa> boolean exists(Example<S> example) { return false; }
        @Override public <S extends ConfiguracionEmpresa, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
    }
}
