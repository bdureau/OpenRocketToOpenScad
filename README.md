# OpenRocketToOpenScad
This is a command line Java program that allow converting OpenRocket files to OpenScad.
First you need to change the extension of your ork file to zip.
Then unzip it.
Get the rocket.ork file and run the following from command line
java -jar OpenRocketToOpenScad.jar rocket.ork

This will extract all components to separate OpenScad files. Those can then be converted to SVG or STL files and used for your CNC router or 3D printer.
 
Currently supported components are:
bulkhead
freeformfinset
trapezoidfinset
centeringring
ellipticalfinset