import graphviz

# Specify the path to your DOT file
dot_file_path = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/Graph50.ngs.perturbed.dot"

# Read and parse the DOT file
graph = graphviz.Source.from_file(dot_file_path)

# Display the parsed graph
graph.view()
# Save the parsed graph as a PDF file
graph.render(filename="output", format="pdf")
