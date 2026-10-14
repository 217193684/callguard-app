package za.jobradar.cv

/**
 * Dictionaries used for free, on-phone matching. Matching is by whole word/phrase,
 * case-insensitive. Aliases on the right map onto the display name on the left.
 */
object Skills {
    val SKILLS: Map<String, List<String>> = linkedMapOf(
        // GIS & spatial
        "GIS" to listOf("gis", "geographic information system"),
        "ArcGIS Pro" to listOf("arcgis pro"),
        "ArcGIS Online" to listOf("arcgis online", "agol"),
        "ArcGIS Enterprise" to listOf("arcgis enterprise", "portal for arcgis"),
        "ArcGIS" to listOf("arcgis", "arcmap", "esri"),
        "QGIS" to listOf("qgis"),
        "PostGIS" to listOf("postgis"),
        "PostgreSQL" to listOf("postgresql", "postgres"),
        "SQL" to listOf("sql", "t-sql", "pl/pgsql", "plpgsql"),
        "FME" to listOf("fme", "safe software"),
        "Spatial analysis" to listOf("spatial analysis", "geoprocessing", "spatial data"),
        "Geospatial" to listOf("geospatial", "geomatics"),
        "Remote sensing" to listOf("remote sensing", "satellite imagery", "imagery"),
        "Cartography" to listOf("cartography", "map production", "mapping"),
        "Utility Network" to listOf("utility network"),
        "Web mapping" to listOf("web mapping", "leaflet", "openlayers", "mapbox", "web gis"),
        "GDAL" to listOf("gdal", "ogr"),
        "GeoPandas" to listOf("geopandas", "shapely"),
        "LiDAR" to listOf("lidar", "point cloud", "point clouds"),
        "Laser scanning" to listOf("laser scanning", "3d scanning", "scan to bim", "terrestrial scanner"),
        "Surveying" to listOf("surveying", "total station", "survey", "rtk", "gnss", "gps"),
        // Telecoms / fibre
        "FTTH/FTTP" to listOf("ftth", "fttp", "fttx", "fibre to the home", "fiber to the home"),
        "Fibre network planning" to listOf("network planning", "fibre planning", "fiber planning", "fibre network", "fiber network", "fibre", "fiber"),
        "OSP design" to listOf("outside plant", "osp"),
        "HLD/LLD" to listOf("hld", "lld", "high level design", "low level design"),
        "Splicing" to listOf("splicing", "splice", "fusion splicing"),
        "As-built" to listOf("as-built", "as built", "asbuilt"),
        "BOQ" to listOf("boq", "bill of quantities", "bill of materials", "bom"),
        "GPON" to listOf("gpon", "xgs-pon", "pon"),
        "VETRO" to listOf("vetro", "vetrofibermap", "vetro fibermap"),
        "IQGeo" to listOf("iqgeo"),
        "Comsof" to listOf("comsof"),
        "Telecommunications" to listOf("telecommunications", "telecoms", "telecom"),
        "Wayleaves" to listOf("wayleave", "wayleaves", "permits"),
        // CAD / BIM / design
        "AutoCAD" to listOf("autocad", "auto cad"),
        "Civil 3D" to listOf("civil 3d", "civil3d"),
        "Revit" to listOf("revit"),
        "BIM" to listOf("bim", "building information modelling", "building information modeling"),
        "SketchUp" to listOf("sketchup", "sketch up"),
        "SolidWorks" to listOf("solidworks", "solid works"),
        "Inventor" to listOf("autodesk inventor", "inventor"),
        "MicroStation" to listOf("microstation"),
        "Navisworks" to listOf("navisworks"),
        "Drafting" to listOf("draughting", "drafting", "technical drawing", "draughtsman", "draftsman", "shop drawings"),
        "3D modelling" to listOf("3d modelling", "3d modeling", "3d model"),
        "SigmaNEST" to listOf("sigmanest", "cnc", "laser cutting", "nesting"),
        // Data & software
        "Python" to listOf("python", "pandas", "numpy"),
        "JavaScript" to listOf("javascript", "typescript", "node.js", "nodejs", "react"),
        "Java" to listOf("java", "spring boot"),
        "C#/.NET" to listOf("c#", ".net", "dotnet"),
        "ETL" to listOf("etl", "data pipeline", "data pipelines", "data integration"),
        "Data engineering" to listOf("data engineering", "data engineer", "data warehouse", "data modelling"),
        "Data analysis" to listOf("data analysis", "data analyst", "analytics", "reporting"),
        "Power BI" to listOf("power bi", "powerbi"),
        "Tableau" to listOf("tableau"),
        "Qlik" to listOf("qlik", "qlikview", "qlik sense"),
        "Excel" to listOf("excel", "spreadsheets", "vba"),
        "APIs" to listOf("api", "apis", "rest api", "fastapi", "flask", "django"),
        "Docker" to listOf("docker", "containers", "kubernetes"),
        "Cloud" to listOf("aws", "azure", "gcp", "google cloud", "cloud"),
        "Git" to listOf("git", "github", "gitlab"),
        "Linux" to listOf("linux", "bash"),
        "Automation" to listOf("automation", "automated", "scripting"),
        "Machine learning" to listOf("machine learning", "artificial intelligence", "llm"),
        // Civil / site
        "Civil engineering" to listOf("civil engineering", "civil engineer"),
        "Reticulation" to listOf("reticulation", "water reticulation", "sewer", "stormwater"),
        "Roads" to listOf("roads", "road maintenance", "resurfacing", "pavement"),
        "Materials testing" to listOf("materials testing", "asphalt", "bitumen", "compaction", "concrete testing"),
        "Construction" to listOf("construction", "site supervision", "site inspection"),
        "Health & safety" to listOf("health and safety", "ohs", "sheq"),
        "Quality assurance" to listOf("quality assurance", "qa", "quality control", "qc"),
        "Inspection" to listOf("inspection", "inspector", "inspect"),
        // People / management
        "Team leadership" to listOf("team lead", "team leader", "leadership", "supervisor", "managed a team"),
        "Project management" to listOf("project management", "project manager", "project coordination", "ms project", "primavera"),
        "Stakeholder management" to listOf("stakeholder", "stakeholders", "client liaison"),
        "Training & mentoring" to listOf("training", "mentoring", "mentor", "onboarding"),
        "Documentation" to listOf("documentation", "technical writing", "sop", "reports"),
        "Driver's licence" to listOf("driver's licence", "drivers licence", "driver’s licence", "code 10", "code c1", "code 08", "code b", "valid licence"),
        "Logistics" to listOf("logistics", "shipping", "freight", "warehouse"),
        "Customer service" to listOf("customer service", "call centre", "client service"),
    )

    /** Job titles used to build CV-based searches. Ordered roughly specific → generic. */
    val TITLES: List<String> = listOf(
        "fibre network planner", "fiber network planner", "network planner", "fibre planner", "fiber planner",
        "ftth designer", "fttp designer", "osp engineer", "outside plant engineer", "network designer",
        "splice planner", "lead network planner", "design team lead",
        "gis analyst", "gis specialist", "gis technician", "gis engineer", "gis developer", "gis coordinator",
        "senior gis analyst", "geospatial analyst", "geospatial engineer", "geospatial data engineer",
        "spatial data engineer", "location intelligence analyst", "fme consultant",
        "data engineer", "data analyst", "bi developer", "database engineer", "database administrator",
        "python developer", "software developer",
        "bim modeler", "bim modeller", "bim technician", "revit technician",
        "cad technician", "cad designer", "cad draughtsman", "draughtsman", "draftsman", "drafter",
        "civil technician", "civil engineering technician", "survey technician", "surveyor",
        "technical production specialist", "project coordinator", "project manager", "quality inspector",
    )

    fun phraseRegex(phrase: String): Regex =
        Regex("(?<![a-z0-9])" + Regex.escape(phrase) + "(?![a-z0-9])", RegexOption.IGNORE_CASE)

    private val skillRegexes: Map<String, List<Regex>> by lazy {
        SKILLS.mapValues { (_, aliases) -> aliases.map { phraseRegex(it) } }
    }
    private val titleRegexes: Map<String, Regex> by lazy { TITLES.associateWith { phraseRegex(it) } }

    /** Skills present in text, most-mentioned first. */
    fun skillsIn(text: String): List<String> =
        skillRegexes.mapNotNull { (name, regs) ->
            val n = regs.sumOf { it.findAll(text).count() }
            if (n > 0) name to n else null
        }.sortedByDescending { it.second }.map { it.first }

    fun skillSetIn(text: String): Set<String> =
        skillRegexes.filter { (_, regs) -> regs.any { it.containsMatchIn(text) } }.keys

    fun titlesIn(text: String): List<String> =
        titleRegexes.mapNotNull { (t, r) ->
            val n = r.findAll(text).count()
            if (n > 0) t to n else null
        }.sortedByDescending { it.second }.map { it.first }

    /** If a CV mentions no known title, guess some from its skills. */
    fun titlesFromSkills(skills: List<String>): List<String> {
        val s = skills.toSet()
        val out = mutableListOf<String>()
        if ("FTTH/FTTP" in s || "Fibre network planning" in s || "OSP design" in s) out += listOf("fibre network planner", "ftth designer")
        if ("GIS" in s || "ArcGIS" in s || "QGIS" in s) out += listOf("gis analyst", "gis specialist")
        if ("PostGIS" in s || "ETL" in s || "Data engineering" in s) out += "spatial data engineer"
        if ("AutoCAD" in s || "Drafting" in s) out += "cad technician"
        if ("Revit" in s || "BIM" in s) out += "bim technician"
        if ("Civil engineering" in s) out += "civil technician"
        if ("Data analysis" in s || "Power BI" in s) out += "data analyst"
        return out.distinct()
    }
}
