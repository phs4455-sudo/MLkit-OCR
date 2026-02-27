package com.hd.hdmobilepos.model

/**
 * 여권 MRZ(TD3, 2줄 44자) 파서.
 *
 * NOTE: 검증(체크디지트)은 MRZUtils.lookslikePassportMRZ()에서 수행하고,
 * 이 클래스는 "필드 파싱"을 담당합니다.
 */
class PassportMRZ(mrz: String) {

    val mrz: String = mrz

    val line1: String
    val line2: String

    // 필드
    var documentType: String = "" // P
        private set

    var issuingCountry: String = "" // KOR/CHN...
        private set

    var lastName: String = ""
        private set

    var firstName: String = ""
        private set

    var passportNumber: String = ""
        private set

    var nationality: String = ""
        private set

    var birthDate: String = "" // YYMMDD
        private set

    var sex: String = "" // M/F/X/<
        private set

    var expiryDate: String = "" // YYMMDD
        private set

    init {
        val lines = mrz.split("\n")
        if (lines.size >= 2) {
            line1 = lines[0]
            line2 = lines[1]
            parseMRZ()
        } else {
            line1 = ""
            line2 = ""
        }
    }

    private fun parseMRZ() {
        try {
            // --- Line 2 ---
            // line2는 위치가 비교적 안정적이어서 먼저 파싱해 line1 보정 판단에도 사용합니다.
            passportNumber = line2.substring(0, 9).replace("<", "")
            nationality = normalizeAlpha(line2.substring(10, 13))
            birthDate = normalizeNumeric(line2.substring(13, 19))
            sex = line2.substring(20, 21)
            expiryDate = normalizeNumeric(line2.substring(21, 27))

            // --- Line 1 (기본 TD3 오프셋) ---
            documentType = normalizeAlpha(line1.substring(0, 1))
            val standardIssuing = normalizeAlpha(line1.substring(2, 5))
            val standardName = parseNameField(line1.substring(5))

            issuingCountry = standardIssuing
            lastName = standardName.first
            firstName = standardName.second

            // OCR이 line1 앞부분(발행국 3자리)을 놓친 경우: P<NELSON<<CALLIE...
            // - 기본 파싱이면 성이 SON으로 잘릴 수 있어, nationality(line2)와 비교해 fallback 적용
            if (shouldApplyMissingCountryFallback(standardIssuing, standardName.first, nationality)) {
                val fallbackName = parseNameField(line1.substring(2))
                if (fallbackName.first.length >= standardName.first.length + 2) {
                    lastName = fallbackName.first
                    firstName = fallbackName.second
                    if (nationality.length == 3) issuingCountry = nationality
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun shouldApplyMissingCountryFallback(
        standardIssuing: String,
        standardLastName: String,
        parsedNationality: String
    ): Boolean {
        if (!line1.startsWith("P<") || line1.length < 8) return false

        // 국가코드가 유효 3글자와 다르거나, line2 nationality와 불일치하면 의심
        val issuingLooksValid = standardIssuing.length == 3 && standardIssuing.all { it in 'A'..'Z' }
        val mismatchWithNationality = parsedNationality.length == 3 && standardIssuing != parsedNationality

        if (!line1.substring(2).contains("<<")) return false
        if (standardLastName.length > 4) return false

        return !issuingLooksValid || mismatchWithNationality
    }

    private fun parseNameField(nameField: String): Pair<String, String> {
        val nameParts = nameField.split("<<", limit = 2)
        val last = if (nameParts.isNotEmpty()) {
            normalizeAlpha(nameParts[0].replace("<", " ").trim())
        } else {
            ""
        }
        val first = if (nameParts.size > 1) {
            normalizeAlpha(nameParts[1].replace("<", " ").trim())
        } else {
            ""
        }
        return Pair(last, first)
    }

    /**
     * 문자만 허용 (숫자 0 → 영문 O로 보정)
     */
    private fun normalizeAlpha(s: String): String {
        return s
            .replace('0', 'O')
            .replace('1', 'I')
            .replace('5', 'S')
            .replace('8', 'B')
            .replace(Regex("[^A-Z ]"), "")
    }

    /**
     * 숫자만 허용 (영문 I → 숫자 1, 영문 O → 숫자 0 보정)
     */
    private fun normalizeNumeric(s: String): String {
        return s
            .replace('I', '1')
            .replace('O', '0')
            .replace('S', '5')
            .replace('B', '8')
            .replace(Regex("[^0-9]"), "")
    }

    override fun toString(): String {
        return "PassportMRZ{" +
            "documentType='$documentType', " +
            "issuingCountry='$issuingCountry', " +
            "lastName='$lastName', " +
            "firstName='$firstName', " +
            "passportNumber='$passportNumber', " +
            "nationality='$nationality', " +
            "birthDate='$birthDate', " +
            "sex='$sex', " +
            "expiryDate='$expiryDate'" +
            "}"
    }
}
