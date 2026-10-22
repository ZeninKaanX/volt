package dev.volt.ui;
import dev.volt.Clip;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class MarkerNavigationTest {
 @Test void skipsCurrentAndFindsNearestInUnsortedBookmarks(){var m=List.of(new Clip.Marker(8,"manual"),new Clip.Marker(2,"combo"),new Clip.Marker(5,"manual"));assertEquals(8,MarkerNavigation.adjacent(m,5,true,10));assertEquals(2,MarkerNavigation.adjacent(m,5,false,10));}
 @Test void excludesInvalidAndFallsBackToClipEdges(){var m=List.of(new Clip.Marker(Double.NaN,""),new Clip.Marker(-1,""),new Clip.Marker(12,""));assertEquals(10,MarkerNavigation.adjacent(m,5,true,10));assertEquals(0,MarkerNavigation.adjacent(m,5,false,10));}
}
