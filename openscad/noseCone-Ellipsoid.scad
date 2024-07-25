/*
*
* Description: this is a simple Elliptical nose cone generated for OpenSCAD
*               you can compile it and export it to an STL file so that it can be 3d printed
*               Note that compiling with openScad can take a long time
*
*
*/

length = ##length##;
aftradius = ##aftradius##;
thickness = ##thickness##;
Rshoulder=##aftshoulderradius##;
Hshoulder=##aftshoulderlength##;
TShoulder=##aftshoulderthickness##;
N=##N##;

/*length = 100;
aftradius = 10;
thickness = 1.5;
Rshoulder=8.5;
Hshoulder=10;
TShoulder=1.5;*/
$fn=200;
module ogive(L,R) { 
 nof=L/2;
 H2=L/nof;

	for (i=[1:nof]) {
		assign(
        A1 = R * sqrt(1 - (pow(((L/nof)*(i-1)), 2) / pow(L,2))),
		A2 = R * sqrt(1 - (pow(((L/nof)*i), 2) / pow(L,2))),
		H1= (L/nof)*i)
		{
			translate ([0,0, L- H1]) cylinder (r1 = A2, r2 = A1 , h= H2, center = true);
		}
	}
}

//nose cone itself
difference() {
ogive(length,aftradius);
translate([0,0,+thickness])ogive(length,aftradius-thickness);
}

//shoulder
translate([0,0,length-thickness+Hshoulder/2])difference() {
    cylinder(r=Rshoulder, h=Hshoulder, center = true);
    cylinder(r=Rshoulder-thickness, h=Hshoulder, center =true);
}

