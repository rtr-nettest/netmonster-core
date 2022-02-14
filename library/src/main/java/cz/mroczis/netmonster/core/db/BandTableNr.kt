package cz.mroczis.netmonster.core.db

import cz.mroczis.netmonster.core.db.model.BandEntity
import cz.mroczis.netmonster.core.db.model.IBandEntity
import cz.mroczis.netmonster.core.model.band.BandNr

/**
 * In NR world loads of ARFCNs are overlapping.
 * This class holds all known bands for release 16.0.0 that have downlink defined.
 *
 * [3GPP 38.101-1 specification for NR](https://portal.3gpp.org/desktopmodules/Specifications/SpecificationDetails.aspx?specificationId=3283)
 */
object BandTableNr {

    private const val SMALLEST_BANDWIDTH = 5_000 // kHz

    private val bands = arrayOf(
        BandEntity(123_400..130_400, "600", 71,0),
        BandEntity(143_400..145_600, "700", 29,0),
        BandEntity(145_800..149_200, "700", 12,0),
        BandEntity(151_600..160_600, "700", 28,1),
        BandEntity(151_600..153_600, "700", 14,0),
        BandEntity(158_200..164_200, "800", 20,1),
        BandEntity(171_800..178_800, "850", 26,0),
        BandEntity(172_000..175_000, "800", 18,0),
        BandEntity(173_800..178_800, "850", 5,0),
        BandEntity(185_000..192_000, "900", 8,1),
        BandEntity(285_400..286_400, "1500", 51,0),
        BandEntity(285_400..286_400, "1500", 76,0),
        BandEntity(285_400..286_400, "1500", 93,0),
        BandEntity(285_400..286_400, "1500", 91,0),
        BandEntity(286_400..303_400, "1500", 50,0),
        BandEntity(286_400..303_400, "1500", 75,0),
        BandEntity(286_400..303_400, "1500", 92,0),
        BandEntity(286_400..303_400, "1500", 94,0),
        BandEntity(295_000..303_600, "1500", 74,0),
        BandEntity(361_000..376_000, "1800", 3,1),
        BandEntity(376_000..384_000, "1900", 39,0),
        BandEntity(386_000..398_000, "1900", 2,0),
        BandEntity(386_000..399_000, "1900", 25,0),
        BandEntity(399_000..404_000, "2000", 70,0),
        BandEntity(402_000..405_000, "2000", 34,0),
        BandEntity(422_000..440_000, "2100", 66,0),
        BandEntity(422_000..434_000, "2100", 1,1),
        BandEntity(422_000..440_000, "2100", 65,0),
        BandEntity(460_000..480_000, "2300", 40,0),
        BandEntity(470_000..472_000, "2300", 30,0),
        BandEntity(496_700..499_000, "2500", 53,0),
        BandEntity(499_200..537_999, "2600", 41,0),
        BandEntity(499_200..538_000, "2600", 90,0),
        BandEntity(514_000..524_000, "2600", 38,0),
        BandEntity(524_000..538_000, "2600", 7,0),
        BandEntity(620_000..680_000, "3700", 77,0),
        BandEntity(620_000..653_333, "3500", 78,1),
        BandEntity(636_667..646_666, "3600", 48,0),
        BandEntity(693_334..733_333, "4500", 79,0)
    )

    internal fun get(arfcn: Int, bandHints: IntArray = intArrayOf()): IBandEntity? {
        val candidates = bands
            .filter { it.channelRange.contains(arfcn) }
            .filter { bandHints.isEmpty() || (it.number != null && bandHints.contains(it.number)) }

        when {
            candidates.isEmpty() -> return null
            candidates.size == 1 -> return candidates[0]
            else -> {
                //return the candidate with highest priority, if priority has a unique highest candidate
                val sortedCandidates = candidates.sortedByDescending { candidate -> candidate.priority }
                if (sortedCandidates[0].priority != sortedCandidates[1].priority) {
                    return sortedCandidates[0]
                }

                // Multiple bands can contain specified arfcn.
                // Let's try find proper one using several small hacks.
                val filtered = candidates.filter { candidate ->
                    // In this snippet we assume 5 MHz as smallest bandwidth
                    // Note that not all bands do support 5 MHz BW but it's the smallest possible value
                    // Also we assume that there are no gaps non-5 MHz between blocks and 1st assigned one's
                    // arfcn is the lowest possible for given band
                    val startFrequency = getFrequency(candidate.channelRange.last)
                    val frequency = getFrequency(arfcn)

                    (startFrequency - frequency).rem(SMALLEST_BANDWIDTH) == 0
                }

                return if (filtered.isEmpty()) {
                    val uniqueName = candidates.distinctBy { it.name }
                    if (uniqueName.size == 1) {
                        // Safest bounds when it comes to bands - take min from start max from end
                        val min = candidates.minOf { it.channelRange.first }
                        val max = candidates.maxOf { it.channelRange.last }
                        uniqueName[0].copy(
                            channelRange = min..max,
                            number = null
                        )
                    } else {
                        null
                    }
                } else if (filtered.size == 1) {
                    filtered[0]
                } else {
                    // Multiple bands do fit - generally this can happen in real world
                    // If at least names of all bands match we'll return a bit accurate data...
                    val uniqueName = filtered.distinctBy { it.name }
                    if (uniqueName.size == 1) {
                        uniqueName[0].copy(number = null)
                    } else {
                        null
                    }
                }
            }
        }
    }

    /**
     * Calculates frequency from arfcn.
     *
     * Taken from 3GPP 38.101-1 / 5.4.2.1 NR-ARFCN and channel raster
     * @return downlink in kHz
     */
    private fun getFrequency(arfcn: Int): Int {
        return if (arfcn <= 600_000) {
            5 * arfcn
        } else {
            3_000_000 + 15 * (arfcn - 600_000)
        }
    }

    /**
     * Attempts to find current band information depending on [arfcn].
     * If no such band is found then result [BandNr] will contain only [BandNr.downlinkArfcn].
     */
    fun map(arfcn: Int, bandHints: IntArray = intArrayOf()): BandNr {
        val raw = get(arfcn, bandHints)
        return BandNr(
            downlinkArfcn = arfcn,
            downlinkFrequency = getFrequency(arfcn),
            number = raw?.number,
            name = raw?.name
        )
    }

}

