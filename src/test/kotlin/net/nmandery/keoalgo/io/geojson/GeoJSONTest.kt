package net.nmandery.keoalgo.io.geojson

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.matchers.string.shouldNotBeEmpty
import io.kotlintest.matchers.types.shouldNotBeNull
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.impl.CoordinateArraySequenceFactory
import org.locationtech.jts.io.WKTReader

class Animal(
    var name: String = "",
    var age: Int = 0
)

@JsonPropertyOrder("type", "id", "geometry", "properties")
@JsonTypeName("Feature")
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
    property = "type"
)
class AnimalFeature : Feature<Point, Animal> {
    constructor(location: Point, animal: Animal) : super(location, animal)
    constructor() : super()
}

@JsonPropertyOrder("type", "features")
@JsonTypeName("FeatureCollection")
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
    property = "type"
)
class AnimalFeatureCollection : FeatureCollection<AnimalFeature> {
    constructor(features: List<AnimalFeature>) : super(features)
    constructor() : super()
}


class GeoJSONTest : StringSpec({

    val gf = GeometryFactory()

    fun makePoint(x: Double, y: Double) =
        Point(CoordinateArraySequenceFactory.instance().create(arrayOf(Coordinate(x, y))), gf)

    fun objectMapper(): ObjectMapper {
        val om = jacksonObjectMapper()
        om.registerJTSGeoJSON()
        return om
    }

    fun testUsingWkt(wkt: String) {
        val wktReader = WKTReader()
        val geomIn = wktReader.read(wkt)
        geomIn.shouldNotBeNull()

        val om = objectMapper()
        val geomSerialized = om.writeValueAsString(geomIn)
        geomSerialized.shouldNotBeEmpty()

        val geomOut = om.readValue(geomSerialized, geomIn.javaClass)
        geomOut.shouldNotBeNull()
        if (geomIn is GeometryCollection) {
            (geomOut is GeometryCollection).shouldBe(true)
            val gcOut = geomOut as GeometryCollection
            gcOut.numGeometries.shouldBe(geomIn.numGeometries)
            (0 until geomIn.numGeometries)
                .forEach { gcOut.getGeometryN(it).shouldBe(geomIn.getGeometryN(it)) }
        } else {
            geomOut.shouldBe(geomIn)
            geomOut.equals(geomIn).shouldBe(true)
        }
    }

    "point" {
        testUsingWkt("POINT(15 20)")
    }

    "linestring" {
        testUsingWkt("LINESTRING(0 0, 10 10, 20 25, 50 60)")
    }

    "polygon" {
        testUsingWkt("POLYGON((0 0,10 0,10 10,0 10,0 0),(5 5,7 5,7 7,5 7, 5 5))")
    }

    "multipoint" {
        testUsingWkt("MULTIPOINT(0 0, 20 20, 60 60)")
    }

    "multilinestring" {
        testUsingWkt("MULTILINESTRING((10 10, 20 20), (15 15, 30 15))")
    }

    "multipolygon" {
        testUsingWkt("MULTIPOLYGON(((0 0,10 0,10 10,0 10,0 0)),((5 5,7 5,7 7,5 7, 5 5)))")
    }

    "geometrycollection" {
        testUsingWkt("GEOMETRYCOLLECTION(POINT(10 10), POINT(30 30), LINESTRING(15 15, 20 20))")
    }

    fun compareAnimalFeatures(f: AnimalFeature, expected: AnimalFeature) {
        expected.shouldNotBeNull()
        expected.geometry?.shouldBe(f.geometry)
        expected.properties?.name.shouldBe(f.properties?.name)
        expected.properties?.age.shouldBe(f.properties?.age)
    }

    "feature" {
        val dog = AnimalFeature(
            makePoint(32.6, 12.3),
            Animal("Brutus", 4)
        )
        val om = objectMapper()
        val dogSerialized = om.writeValueAsString(dog)
        dogSerialized.shouldContain(""""type":"Feature"""")

        val dogDeserialized = om.readValue(dogSerialized, AnimalFeature::class.java)
        compareAnimalFeatures(dog, dogDeserialized)
    }

    "featurecollection" {
        val fc = AnimalFeatureCollection(
            listOf(
                AnimalFeature(
                    makePoint(32.6, 12.3),
                    Animal("Brutus", 4)
                ),
                AnimalFeature(
                    makePoint(45.1, 19.8),
                    Animal("Tweety", 2)

                )
            )
        )

        val om = objectMapper()
        val fcSerialized = om.writeValueAsString(fc)
        fcSerialized.shouldContain(""""type":"Feature"""")
        fcSerialized.shouldContain(""""type":"FeatureCollection"""")

        val fcDeserialized = om.readValue(fcSerialized, AnimalFeatureCollection::class.java)
        fcDeserialized.size().shouldBe(fc.size())
        (0 until fc.size())
            .forEach { compareAnimalFeatures(fcDeserialized.features[it], fc.features[it]) }
    }
})
