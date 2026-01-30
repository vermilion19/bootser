package com.booster.ddayservice.specialday.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.ZoneId;

@Getter
@RequiredArgsConstructor
public enum Timezone {

    // Africa
    AFRICA_ACCRA("Africa/Accra"),
    AFRICA_BANJUL("Africa/Banjul"),
    AFRICA_BRAZZAVILLE("Africa/Brazzaville"),
    AFRICA_CAIRO("Africa/Cairo"),
    AFRICA_CASABLANCA("Africa/Casablanca"),
    AFRICA_GABORONE("Africa/Gaborone"),
    AFRICA_HARARE("Africa/Harare"),
    AFRICA_JOHANNESBURG("Africa/Johannesburg"),
    AFRICA_KINSHASA("Africa/Kinshasa"),
    AFRICA_LAGOS("Africa/Lagos"),
    AFRICA_LIBREVILLE("Africa/Libreville"),
    AFRICA_MAPUTO("Africa/Maputo"),
    AFRICA_MASERU("Africa/Maseru"),
    AFRICA_NAIROBI("Africa/Nairobi"),
    AFRICA_NIAMEY("Africa/Niamey"),
    AFRICA_PORTO_NOVO("Africa/Porto-Novo"),
    AFRICA_TUNIS("Africa/Tunis"),
    AFRICA_WINDHOEK("Africa/Windhoek"),

    // America
    AMERICA_ARGENTINA_BUENOS_AIRES("America/Argentina/Buenos_Aires"),
    AMERICA_ASUNCION("America/Asuncion"),
    AMERICA_BARBADOS("America/Barbados"),
    AMERICA_BELIZE("America/Belize"),
    AMERICA_BOGOTA("America/Bogota"),
    AMERICA_CARACAS("America/Caracas"),
    AMERICA_CHICAGO("America/Chicago"),
    AMERICA_COSTA_RICA("America/Costa_Rica"),
    AMERICA_DENVER("America/Denver"),
    AMERICA_EL_SALVADOR("America/El_Salvador"),
    AMERICA_GRENADA("America/Grenada"),
    AMERICA_GUATEMALA("America/Guatemala"),
    AMERICA_GUAYAQUIL("America/Guayaquil"),
    AMERICA_GUYANA("America/Guyana"),
    AMERICA_HAVANA("America/Havana"),
    AMERICA_JAMAICA("America/Jamaica"),
    AMERICA_LA_PAZ("America/La_Paz"),
    AMERICA_LIMA("America/Lima"),
    AMERICA_LOS_ANGELES("America/Los_Angeles"),
    AMERICA_MANAGUA("America/Managua"),
    AMERICA_MEXICO_CITY("America/Mexico_City"),
    AMERICA_MONTERREY("America/Monterrey"),
    AMERICA_MONTEVIDEO("America/Montevideo"),
    AMERICA_MONTSERRAT("America/Montserrat"),
    AMERICA_NASSAU("America/Nassau"),
    AMERICA_NEW_YORK("America/New_York"),
    AMERICA_NUUK("America/Nuuk"),
    AMERICA_PANAMA("America/Panama"),
    AMERICA_PARAMARIBO("America/Paramaribo"),
    AMERICA_PORT_AU_PRINCE("America/Port-au-Prince"),
    AMERICA_PUERTO_RICO("America/Puerto_Rico"),
    AMERICA_SANTIAGO("America/Santiago"),
    AMERICA_SANTO_DOMINGO("America/Santo_Domingo"),
    AMERICA_SAO_PAULO("America/Sao_Paulo"),
    AMERICA_TEGUCIGALPA("America/Tegucigalpa"),
    AMERICA_TORONTO("America/Toronto"),
    AMERICA_VANCOUVER("America/Vancouver"),

    // Arctic
    ARCTIC_LONGYEARBYEN("Arctic/Longyearbyen"),

    // Asia
    ASIA_ALMATY("Asia/Almaty"),
    ASIA_HONG_KONG("Asia/Hong_Kong"),
    ASIA_HO_CHI_MINH("Asia/Ho_Chi_Minh"),
    ASIA_JAKARTA("Asia/Jakarta"),
    ASIA_MANILA("Asia/Manila"),
    ASIA_NICOSIA("Asia/Nicosia"),
    ASIA_SEOUL("Asia/Seoul"),
    ASIA_SHANGHAI("Asia/Shanghai"),
    ASIA_SINGAPORE("Asia/Singapore"),
    ASIA_TBILISI("Asia/Tbilisi"),
    ASIA_TOKYO("Asia/Tokyo"),
    ASIA_ULAANBAATAR("Asia/Ulaanbaatar"),
    ASIA_YEREVAN("Asia/Yerevan"),

    // Atlantic
    ATLANTIC_FAROE("Atlantic/Faroe"),
    ATLANTIC_REYKJAVIK("Atlantic/Reykjavik"),

    // Australia
    AUSTRALIA_SYDNEY("Australia/Sydney"),

    // Europe
    EUROPE_AMSTERDAM("Europe/Amsterdam"),
    EUROPE_ANDORRA("Europe/Andorra"),
    EUROPE_ATHENS("Europe/Athens"),
    EUROPE_BELGRADE("Europe/Belgrade"),
    EUROPE_BERLIN("Europe/Berlin"),
    EUROPE_BRATISLAVA("Europe/Bratislava"),
    EUROPE_BRUSSELS("Europe/Brussels"),
    EUROPE_BUCHAREST("Europe/Bucharest"),
    EUROPE_BUDAPEST("Europe/Budapest"),
    EUROPE_CHISINAU("Europe/Chisinau"),
    EUROPE_COPENHAGEN("Europe/Copenhagen"),
    EUROPE_DUBLIN("Europe/Dublin"),
    EUROPE_GIBRALTAR("Europe/Gibraltar"),
    EUROPE_GUERNSEY("Europe/Guernsey"),
    EUROPE_HELSINKI("Europe/Helsinki"),
    EUROPE_ISLE_OF_MAN("Europe/Isle_of_Man"),
    EUROPE_ISTANBUL("Europe/Istanbul"),
    EUROPE_JERSEY("Europe/Jersey"),
    EUROPE_KYIV("Europe/Kyiv"),
    EUROPE_LISBON("Europe/Lisbon"),
    EUROPE_LJUBLJANA("Europe/Ljubljana"),
    EUROPE_LONDON("Europe/London"),
    EUROPE_LUXEMBOURG("Europe/Luxembourg"),
    EUROPE_MADRID("Europe/Madrid"),
    EUROPE_MALTA("Europe/Malta"),
    EUROPE_MINSK("Europe/Minsk"),
    EUROPE_MONACO("Europe/Monaco"),
    EUROPE_MOSCOW("Europe/Moscow"),
    EUROPE_OSLO("Europe/Oslo"),
    EUROPE_PARIS("Europe/Paris"),
    EUROPE_PODGORICA("Europe/Podgorica"),
    EUROPE_PRAGUE("Europe/Prague"),
    EUROPE_RIGA("Europe/Riga"),
    EUROPE_ROME("Europe/Rome"),
    EUROPE_SAN_MARINO("Europe/San_Marino"),
    EUROPE_SARAJEVO("Europe/Sarajevo"),
    EUROPE_SKOPJE("Europe/Skopje"),
    EUROPE_SOFIA("Europe/Sofia"),
    EUROPE_STOCKHOLM("Europe/Stockholm"),
    EUROPE_TALLINN("Europe/Tallinn"),
    EUROPE_TIRANE("Europe/Tirane"),
    EUROPE_VADUZ("Europe/Vaduz"),
    EUROPE_VATICAN("Europe/Vatican"),
    EUROPE_VIENNA("Europe/Vienna"),
    EUROPE_VILNIUS("Europe/Vilnius"),
    EUROPE_WARSAW("Europe/Warsaw"),
    EUROPE_ZAGREB("Europe/Zagreb"),
    EUROPE_ZURICH("Europe/Zurich"),

    // Indian
    INDIAN_ANTANANARIVO("Indian/Antananarivo"),

    // Pacific
    PACIFIC_AUCKLAND("Pacific/Auckland"),
    PACIFIC_PORT_MORESBY("Pacific/Port_Moresby"),

    // UTC
    UTC("UTC"),
    ;

    private final String zoneId;

    public ZoneId toZoneId() {
        return ZoneId.of(zoneId);
    }
}
