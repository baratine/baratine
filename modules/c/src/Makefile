SHELL=/bin/sh
srcdir = .
top_srcdir = ../../..
plugins=baratine common baratine ssl

all	: plugins

plugins	:
	for dir in $(plugins); do (cd $$dir; $(MAKE)); done

clean	:
	for dir in common $(plugins); do \
	  (cd $$dir; $(MAKE) clean); \
        done

install	:
	@ echo "Installing configured Baratine plugins $(plugins)"
	@ for dir in $(plugins); do \
	  (cd $$dir; $(MAKE) install); \
	done
