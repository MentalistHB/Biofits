package com.thbproject.biofits.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thbproject.biofits.configuration.AppSettings;
import com.thbproject.biofits.exception.ResourceNotFoundException;
import com.thbproject.biofits.model.Aktivitaet;
import com.thbproject.biofits.model.Aktivitaet_;
import com.thbproject.biofits.model.Benutzer;
import com.thbproject.biofits.model.SportArt;
import com.thbproject.biofits.repository.AktivitaetRepository;
import com.thbproject.biofits.repository.BenutzerRepository;
import com.thbproject.biofits.repository.SportArtRepository;
import com.thbproject.biofits.search.aktivitaet.AktivitaetCriteria;
import com.thbproject.biofits.search.aktivitaet.AktivitaetSpecs;

@Service
@Transactional(rollbackFor = Exception.class)
public class AktivitaetService {
    @Autowired
    private BenutzerRepository benutzerRepository;
    @Autowired
    private AktivitaetRepository aktivitaetRepository;
    @Autowired
    private SportArtRepository sportArtRepository;
    @Autowired
    private ZielRechnungService zielRechnungService;
    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private AppSettings settings;

    public Aktivitaet start(
            Long benutzerId,
            Long sportArtId) {

        // create the new benutzer
        Benutzer benutzer = findBenutzer(benutzerId);

        SportArt sportArt = findSportArt(sportArtId);

        AktivitaetCriteria criteria = new AktivitaetCriteria();
        criteria.setBenutzerId(benutzerId);
        criteria.setSportArtId(sportArtId);

        AktivitaetSpecs specs = new AktivitaetSpecs(criteria);

        List<Aktivitaet> aktivitaeten = aktivitaetRepository.findAll(specs);

        Aktivitaet aktivitaet;

        if (aktivitaeten.isEmpty()) {
            aktivitaet = new Aktivitaet();
            aktivitaet.setBenutzer(benutzer);
            aktivitaet.setSportArt(sportArt);
            aktivitaet.setDistanz((double) 0);
            aktivitaet.setStartDatum(new Date());
            aktivitaet.setEndDatum(DateUtils.addHours(aktivitaet.getStartDatum(), settings.getActivityDuration()));
            aktivitaet.setAktuelStartDatum(new Date());
            aktivitaet.setAktuelEndDatum(null);
            aktivitaet.setZiel(zielRechnungService.rechnen(benutzerId, sportArtId, 1));
        } else {
            criteria = new AktivitaetCriteria();
            criteria.setBenutzerId(benutzerId);
            criteria.setSportArtId(sportArtId);
            criteria.setRunning(true);

            specs = new AktivitaetSpecs(criteria);

            aktivitaeten = aktivitaetRepository.findAll(specs);
            if (aktivitaeten.isEmpty()) {
                aktivitaet = new Aktivitaet();
                aktivitaet.setBenutzer(benutzer);
                aktivitaet.setSportArt(sportArt);
                aktivitaet.setDistanz((double) 0);
                aktivitaet.setStartDatum(new Date());
                aktivitaet.setEndDatum(DateUtils.addDays(aktivitaet.getStartDatum(), settings.getActivityDuration()));
                aktivitaet.setAktuelStartDatum(new Date());
                aktivitaet.setAktuelEndDatum(null);
                aktivitaet.setZiel(zielRechnungService.rechnen(benutzerId, sportArtId, 1));
            } else {
                aktivitaet = aktivitaeten.get(0);
                aktivitaet.setAktuelStartDatum(new Date());
                aktivitaet.setAktuelEndDatum(null);
            }
        }

        return aktivitaetRepository.save(aktivitaet);
    }

    public void stop(
            Long benutzerId,
            Long sportArtId) {

        AktivitaetCriteria criteria = new AktivitaetCriteria();
        criteria.setBenutzerId(benutzerId);
        criteria.setSportArtId(sportArtId);

        AktivitaetSpecs specs = new AktivitaetSpecs(criteria);

        List<Aktivitaet> aktivitaeten = aktivitaetRepository.findAll(specs);
        aktivitaeten.forEach(e -> {
            if (e.getAktuelEndDatum() == null) {
                e.setAktuelEndDatum(new Date());
                aktivitaetRepository.save(e);
            }
        });
    }

    public Aktivitaet getByDate(Long benutzerId, Long sportArtId, Date date) {

        AktivitaetCriteria criteria = new AktivitaetCriteria();
        criteria.setBenutzerId(benutzerId);
        criteria.setSportArtId(sportArtId);
        criteria.setDate(date);

        AktivitaetSpecs specs = new AktivitaetSpecs(criteria);

        List<Aktivitaet> aktivitaeten = aktivitaetRepository.findAll(specs);

        return aktivitaeten.isEmpty() ? null : aktivitaeten.get(0);
    }

    public JsonNode list(
            AktivitaetCriteria criteria,
            Long sportArtId,
            String period) {

        if (criteria == null) {
            criteria = new AktivitaetCriteria();
        }

        AktivitaetSpecs specs = new AktivitaetSpecs(criteria);

        Sort sort = new Sort(Sort.Direction.ASC, Aktivitaet_.startDatum.getName());

        List<Aktivitaet> aktivitaeten = aktivitaetRepository.findAll(specs, sort);

        if (sportArtId == null && (period == null || period.isEmpty())) {
            return mapper.valueToTree(aktivitaeten);
        } else {
            if (sportArtId != null) {
                Map<String, List<Aktivitaet>> aktivitaetenBySportArt = new HashMap<>();
                aktivitaeten.forEach(e -> {
                    List<Aktivitaet> actual = aktivitaetenBySportArt.getOrDefault(e.getSportArt().getName(), new ArrayList<>());
                    actual.add(e);

                    aktivitaetenBySportArt.put(
                            e.getSportArt().getName(),
                            actual);
                });

                if (period == null || period.isEmpty()) {
                    return mapper.valueToTree(aktivitaetenBySportArt);
                } else {

                    Map<String, Map<LocalDate, List<Aktivitaet>>> resultBySportArt = new HashMap<>();
                    aktivitaetenBySportArt.keySet().forEach(k -> {

                        final Map<String, TemporalAdjuster> ADJUSTERS = new HashMap<>();

                        ADJUSTERS.put("day", TemporalAdjusters.ofDateAdjuster(d -> d)); // identity
                        ADJUSTERS.put("week", TemporalAdjusters.previousOrSame(DayOfWeek.of(1)));
                        ADJUSTERS.put("month", TemporalAdjusters.firstDayOfMonth());
                        ADJUSTERS.put("year", TemporalAdjusters.firstDayOfYear());

                        Map<LocalDate, List<Aktivitaet>> result = aktivitaetenBySportArt.get(k).stream()
                                .collect(Collectors.groupingBy(e -> e.getStartDatum().toInstant().atZone(ZoneId.systemDefault())
                                        .toLocalDate().with(ADJUSTERS.get(getGroupBy(period)))));

                        resultBySportArt.put(k, result);
                    });

                    return mapper.valueToTree(resultBySportArt);
                }

            } else {
                List<Aktivitaet> aktivitaetenBySportArt = aktivitaeten.stream()
                        .filter(e -> e.getSportArt().getId().equals(sportArtId)).collect(Collectors.toList());

                final Map<String, TemporalAdjuster> ADJUSTERS = new HashMap<>();

                ADJUSTERS.put("day", TemporalAdjusters.ofDateAdjuster(d -> d)); // identity
                ADJUSTERS.put("week", TemporalAdjusters.previousOrSame(DayOfWeek.of(1)));
                ADJUSTERS.put("month", TemporalAdjusters.firstDayOfMonth());
                ADJUSTERS.put("year", TemporalAdjusters.firstDayOfYear());

                Map<LocalDate, List<Aktivitaet>> result = aktivitaetenBySportArt.stream()
                        .collect(Collectors.groupingBy(e -> e.getStartDatum().toInstant().atZone(ZoneId.systemDefault())
                                .toLocalDate().with(ADJUSTERS.get(getGroupBy(period)))));
                return mapper.valueToTree(result);
            }
        }
    }

    public Aktivitaet current(
            Long benutzerId,
            Long sportArtId) {

        // create the new benutzer
        Benutzer benutzer = findBenutzer(benutzerId);

        SportArt sportArt = findSportArt(sportArtId);

        Date date = new Date();

        List<Aktivitaet> aktivitaeten = aktivitaetRepository.findByBenutzerIdAndSportArtIdOrderByStartDatumAsc(benutzerId,
                sportArtId);

        // check if activity runs
        Aktivitaet aktivitaet;

        if (aktivitaeten.isEmpty()) {
            aktivitaet = new Aktivitaet();
            aktivitaet.setBenutzer(benutzer);
            aktivitaet.setSportArt(sportArt);
            aktivitaet.setDistanz((double) 0);
            aktivitaet.setStartDatum(new Date());
            aktivitaet.setEndDatum(DateUtils.addSeconds(aktivitaet.getStartDatum(), settings.getActivityDuration()));
            aktivitaet.setAktuelStartDatum(new Date());
            aktivitaet.setAktuelEndDatum(null);
            aktivitaet.setZiel(zielRechnungService.rechnen(benutzerId, sportArtId, 1));
        } else {
            aktivitaet = aktivitaeten.get(0);
            if (aktivitaet.getEndDatum().before(date)) {
                aktivitaet = new Aktivitaet();
                aktivitaet.setBenutzer(benutzer);
                aktivitaet.setSportArt(sportArt);
                aktivitaet.setDistanz((double) 0);
                aktivitaet.setStartDatum(new Date());
                aktivitaet.setEndDatum(DateUtils.addSeconds(aktivitaet.getStartDatum(), settings.getActivityDuration()));
                aktivitaet.setAktuelStartDatum(new Date());
                aktivitaet.setAktuelEndDatum(null);
                aktivitaet.setZiel(zielRechnungService.rechnen(benutzerId, sportArtId, 1));
            }
        }

        return aktivitaet;
    }

    private Benutzer findBenutzer(Long id) {
        // find benutzer
        Benutzer benutzer = benutzerRepository.findOne(id);
        if (benutzer == null) {
            throw new ResourceNotFoundException(
                    String.format("The benutzer with the id %s does not exist", id.toString()));
        }

        return benutzer;
    }

    private SportArt findSportArt(Long id) {
        // find sportArt
        SportArt sportArt = sportArtRepository.findOne(id);
        if (sportArt == null) {
            throw new ResourceNotFoundException(
                    String.format("The sportArt with the id %s does not exist", id.toString()));
        }

        return sportArt;
    }

    private String getGroupBy(String groupBy) {
        if (groupBy.toLowerCase().equals("day") || groupBy.toLowerCase().equals("week")
                || groupBy.toLowerCase().equals("month") || groupBy.toLowerCase().equals("year")) {
            return groupBy.toLowerCase();
        }

        return "day";
    }

}
