import sys
def main():
	output = ""
	for line in sys.stdin:
		output = output + line
	if output.count('Received: {"insertCount":150,"success":true}') < 12:
		print "Failed: Not enough successful posts to OLL"
	elif output.count('Received: {"insertCount":150,"success":true}') > 12:
		print "Failed: Too many successful posts to OLL"

main()