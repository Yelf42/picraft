# Picraft
Picraft is a plugin for PaperMC 1.21.11 that adds nonograms to Minecraft for players to work together to solve.

## Commands

``/picraft:buildRandom dimension x y z width height``</br>
Creates a new fully random nonogram with width * height at the dimension and location. </br>
Very fast, but will likely create a nonogram with multiple solutions. </br>
The nonogram will be considered solved if any solution is input, </br>
but these problems can be more tedious to try solve.

``/picraft:buildUnique dimension x y z width height``</br>
Creates a new random nonogram with width * height at the dimension and location. </br>
Quite slow, but creates a nonogram with a unique solution. </br>
Runs asynchronously, so shouldn't cause hitching even if it takes a while

``/picraft:buildExisting dimension x y z width height encoding``</br>
Creates a nonogram copy using the encoding with width * height at the dimension and location. </br>
Very fast, but the existence of multiple solutions depends on using a good encoding. </br>
Encodings are strings that represent the solution in hexidecimal. </br>
``buildRandom`` and ``buildUnique`` will output the encoding they create, but you can also generate an encoding using [this p5.js project](https://editor.p5js.org/Yelf/sketches/8-t1V3A9n)

``/picraft:buildRepeat dimension x y z``</br>
Creates a copy of the last nonogram created </br>
Very fast, ideal for repeating a `buildRandom` for competitions etc </br>


## Credits
[Nonogram-Maker by kniffen](https://github.com/kniffen/Nonogram-Maker?tab=readme-ov-file)

</br></br>
### AI Disclaimer
The code for generating single-solution nonograms was generated via Claude Sonnet 4.6
