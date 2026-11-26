package com.bomerp.servicios.recordatorio;

import com.bomerp.servicios.orden.entity.OrdenTrabajo;
import com.bomerp.servicios.orden.repository.OrdenTrabajoRepository;
import com.bomerp.config.RecursoNoEncontradoException;
import com.bomerp.config.ReglaNegocioException;
import com.bomerp.notificaciones.notificacion.service.NotificadorCorreo;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.util.HtmlUtils;
import java.time.*;
import java.util.*;

@Service
public class RecordatorioService {
    private final RecordatorioRepository repo;
    private final OrdenTrabajoRepository ordenes;
    private final NotificadorCorreo correo;
    private final String oficina;
    private final TransactionTemplate tx;
    public RecordatorioService(RecordatorioRepository repo,OrdenTrabajoRepository ordenes,NotificadorCorreo correo,
            @Value("${app.notificaciones.correo-oficina:}") String oficina,PlatformTransactionManager tm) {
        this.repo=repo;this.ordenes=ordenes;this.correo=correo;this.oficina=oficina;this.tx=new TransactionTemplate(tm);
    }
    public record Config(boolean activo,@Min(1) @Max(120) int frecuenciaMeses,@Min(0) @Max(30) int anticipacionDias) {}
    public record Estado(boolean activo,int frecuenciaMeses,int anticipacionDias,LocalDate fechaSiguiente,LocalDate fechaAviso,
                         String correoCliente,String correoOficina,String estadoCliente,String estadoOficina,
                         String errorCliente,String errorOficina) {}
    @Transactional(readOnly=true)
    public Estado obtener(Long ordenId) {return repo.findByOrden_Id(ordenId).map(this::estado).orElse(null);}
    @Transactional
    public Estado configurar(Long ordenId,Config c) {
        OrdenTrabajo o=ordenes.bloquear(ordenId).orElseThrow(()->new RecursoNoEncontradoException("Orden no encontrada"));
        if(o.getEstado()!=OrdenTrabajo.Estado.REALIZADA) throw new ReglaNegocioException("El recordatorio se configura después de cerrar la orden.");
        if(c.frecuenciaMeses()<1||c.frecuenciaMeses()>120||c.anticipacionDias()<0||c.anticipacionDias()>30) throw new IllegalArgumentException("Frecuencia de 1 a 120 meses y anticipación de 0 a 30 días.");
        var anterior=repo.findByOrden_Id(ordenId);
        RecordatorioServicio r=anterior.isPresent()?repo.bloquear(anterior.get().getId()).orElseThrow():new RecordatorioServicio();
        if("ENVIANDO".equals(r.getEstadoCliente())||"ENVIANDO".equals(r.getEstadoOficina())) throw new ReglaNegocioException("Hay un envío en curso; espere antes de cambiar el recordatorio.");
        if(!c.activo()){r.setOrden(o);r.setActivo(false);if(r.getFrecuenciaMeses()==0){r.setFrecuenciaMeses(c.frecuenciaMeses());r.setAnticipacionDias(c.anticipacionDias());}return estado(repo.save(r));}
        String cliente=o.getCliente().getCorreo();
        if(!email(cliente)||!email(oficina)) throw new ReglaNegocioException("Registre un correo válido del cliente y configure el correo de oficina para avisar a ambos.");
        LocalDate siguiente=o.getFechaRealizada().plusMonths(c.frecuenciaMeses());
        boolean cambioFecha=r.getFechaSiguiente()==null||!siguiente.equals(r.getFechaSiguiente())||c.anticipacionDias()!=r.getAnticipacionDias();
        boolean cambioCliente=cambioFecha||!cliente.equals(r.getCorreoCliente());
        boolean cambioOficina=cambioFecha||!oficina.equals(r.getCorreoOficina());
        if((cambioCliente&&"INCIERTO".equals(r.getEstadoCliente()))||(cambioOficina&&"INCIERTO".equals(r.getEstadoOficina())))
            throw new ReglaNegocioException("Revise y confirme el reintento del correo incierto antes de cambiar su programación.");
        r.setOrden(o);r.setActivo(true);r.setFrecuenciaMeses(c.frecuenciaMeses());r.setAnticipacionDias(c.anticipacionDias());
        r.setFechaSiguiente(siguiente);r.setFechaAviso(siguiente.minusDays(c.anticipacionDias()));r.setCorreoCliente(cliente);r.setCorreoOficina(oficina);
        if(cambioCliente){r.setEstadoCliente("PENDIENTE");r.setIntentosCliente(0);r.setErrorCliente(null);r.setIntentoCliente(null);}
        if(cambioOficina){r.setEstadoOficina("PENDIENTE");r.setIntentosOficina(0);r.setErrorOficina(null);r.setIntentoOficina(null);}
        return estado(repo.save(r));
    }
    /** Reintento explícito de un resultado incierto: el usuario confirma la posible repetición. */
    @Transactional
    public Estado reintentar(Long ordenId,boolean confirmar) {
        var previo=repo.findByOrden_Id(ordenId).orElseThrow(()->new RecursoNoEncontradoException("Recordatorio no encontrado"));
        var r=repo.bloquear(previo.getId()).orElseThrow();
        if(!confirmar) throw new ReglaNegocioException("Confirme el reintento: un correo incierto podría haber llegado.");
        if("ENVIANDO".equals(r.getEstadoCliente())||"ENVIANDO".equals(r.getEstadoOficina())) throw new ReglaNegocioException("Todavía hay un envío en curso.");
        if(!"ENVIADO".equals(r.getEstadoCliente())){r.setEstadoCliente("PENDIENTE");r.setIntentosCliente(0);r.setIntentoCliente(null);r.setErrorCliente(null);}
        if(!"ENVIADO".equals(r.getEstadoOficina())){r.setEstadoOficina("PENDIENTE");r.setIntentosOficina(0);r.setIntentoOficina(null);r.setErrorOficina(null);}
        return estado(r);
    }
    @Scheduled(cron="${app.recordatorios.cron:0 0 8 * * *}",zone="America/Lima")
    public void procesar(){procesar(LocalDate.now(ZoneId.of("America/Lima")));}
    public void procesar(LocalDate hoy) {
        var ids=tx.execute(s->repo.pendientes(hoy,hoy.atStartOfDay(),LocalDateTime.now().minusMinutes(15),org.springframework.data.domain.PageRequest.of(0,500)));
        for(Long id:Objects.requireNonNull(ids)) for(boolean cliente:new boolean[]{true,false}) {
            Mensaje m=tx.execute(s->reservar(id,cliente,hoy)); if(m==null) continue;
            String error=null;
            try {correo.enviarConAdjuntos(new NotificadorCorreo.MensajeCorreo(m.destino(),"Recordatorio de próximo mantenimiento",m.html(),List.of(),"<mantenimiento-"+id+"-"+(cliente?"cliente":"oficina")+"-"+UUID.randomUUID()+"@sge-jd.local>"));}
            catch(Exception e){error=e instanceof NotificadorCorreo.CorreoNoEnviadoException &&
                    (e.getCause()==null || e.getCause() instanceof org.springframework.mail.MailAuthenticationException)
                    ?"FALLIDO":"INCIERTO";}
            String resultado=error;
            tx.executeWithoutResult(s->{var r=repo.bloquear(id).orElseThrow();String estado=resultado==null?"ENVIADO":resultado;String texto=resultado==null?null:("INCIERTO".equals(resultado)?"No se puede confirmar la entrega. Revise antes de reintentar.":"El correo no fue aceptado. Revise SMTP; se reintenta al día siguiente, hasta 3 intentos.");
                if(cliente){r.setEstadoCliente(estado);r.setErrorCliente(texto);}else{r.setEstadoOficina(estado);r.setErrorOficina(texto);}});
        }
    }
    private record Mensaje(String destino,String html) {}
    private Mensaje reservar(Long id,boolean cliente,LocalDate hoy) {
        var r=repo.bloquear(id).orElseThrow();if(!r.isActivo()||r.getFechaAviso().isAfter(hoy)) return null;
        String estado=cliente?r.getEstadoCliente():r.getEstadoOficina();LocalDateTime ultimo=cliente?r.getIntentoCliente():r.getIntentoOficina();
        if("ENVIANDO".equals(estado)) {
            if(ultimo!=null&&ultimo.isBefore(LocalDateTime.now().minusMinutes(15))){if(cliente){r.setEstadoCliente("INCIERTO");r.setErrorCliente("El proceso se interrumpió durante el envío; verifique la entrega.");}else{r.setEstadoOficina("INCIERTO");r.setErrorOficina("El proceso se interrumpió durante el envío; verifique la entrega.");}}return null;
        }
        int intentos=cliente?r.getIntentosCliente():r.getIntentosOficina();
        if(!Set.of("PENDIENTE","FALLIDO").contains(estado)||intentos>=3||(ultimo!=null&&!ultimo.toLocalDate().isBefore(hoy))) return null;
        String nombre=HtmlUtils.htmlEscape(r.getOrden().getCliente().getNombreORazonSocial());
        String html="<html><body style='font-family:Arial;background:#eef6f9;padding:24px'><div style='max-width:600px;margin:auto;background:white;padding:28px;border-top:5px solid #507b9e'><h2>Es momento de coordinar su mantenimiento</h2><p>Cliente: <strong>"+nombre+"</strong></p><p>Último servicio: "+r.getOrden().getFechaRealizada()+" · Orden #"+r.getOrden().getId()+"</p><p>Próxima fecha recomendada: <strong>"+r.getFechaSiguiente()+"</strong></p><p>Frecuencia: cada "+r.getFrecuenciaMeses()+" meses.</p><p>Comuníquese con JD Refrigeración para coordinar la visita. Este recordatorio no representa una cita confirmada.</p></div></body></html>";
        if(cliente){r.setEstadoCliente("ENVIANDO");r.setIntentosCliente(intentos+1);r.setIntentoCliente(LocalDateTime.now());}else{r.setEstadoOficina("ENVIANDO");r.setIntentosOficina(intentos+1);r.setIntentoOficina(LocalDateTime.now());}
        return new Mensaje(cliente?r.getCorreoCliente():r.getCorreoOficina(),html);
    }
    private static boolean email(String s){return s!=null&&s.length()<=150&&s.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");}
    private Estado estado(RecordatorioServicio r){return new Estado(r.isActivo(),r.getFrecuenciaMeses(),r.getAnticipacionDias(),r.getFechaSiguiente(),r.getFechaAviso(),r.getCorreoCliente(),r.getCorreoOficina(),r.getEstadoCliente(),r.getEstadoOficina(),r.getErrorCliente(),r.getErrorOficina());}
}
