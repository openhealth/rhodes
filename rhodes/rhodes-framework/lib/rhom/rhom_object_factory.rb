#
#  rhom_object_factory.rb
#  rhodes
#  Returns an array of RhomObjects
#
#  Copyright (C) 2008 Rhomobile, Inc. All rights reserved.
#
#  This program is free software: you can redistribute it and/or modify
#  it under the terms of the GNU General Public License as published by
#  the Free Software Foundation, either version 3 of the License, or
#  (at your option) any later version.
#
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU General Public License for more details.
#
#  You should have received a copy of the GNU General Public License
#  along with this program.  If not, see <http://www.gnu.org/licenses/>.
#
require 'rhom'
require 'rho'
require 'rho/rhosupport'

module Rhom
  class RhomObjectFactory
    
    def initialize
	    unless not defined? Rho::RhoConfig.sources
  	    init_objects
  	  end
    end
  
    # Initialize new object with dynamic attributes
    def init_objects
      Rho::RhoConfig.sources.each do |classname,source|
        unless Object.const_defined?(classname.intern)
          Object.const_set(classname.intern, 
            Class.new do
              include ::Rhom::RhomObject
              extend ::Rhom::RhomObject
              
              # This holds the attributes for an instance of
              # the rhom object
              attr_accessor :vars
          
              def initialize(obj=nil)
                @vars = {}
                self.vars['object'] = "#{((Time.now.to_f - Time.mktime(2009,"jan",1,0,0,0,0).to_f) * 10**6).to_i}"
                if obj
                  obj.each do |key,value|
                    self.vars[key] = value
                  end
                end
              end
              
              def method_missing(name, *args)
                unless name == Fixnum
                  varname = name.to_s.gsub(/\=/,"")
                  setting = (name.to_s =~ /=/)
                  if setting
                    @vars[varname] = args[0]  
                  else
                    @vars[varname]
                  end
                end
              end
		  
              class << self
              
                def count
                  ::Rhom::RhomDbAdapter.select_from_table('object_values','object', {"source_id"=>get_source_id}, {"distinct"=>true}).length
                end
              
                def get_source_id
                  Rho::RhoConfig.sources[self.name.to_s]['source_id'].to_s
                end
                
                def get_attribs
                  attribs = ::Rhom::RhomDbAdapter.select_from_table('object_values','attrib', {"source_id"=>get_source_id}, {"distinct"=>true})
                  attribs.collect! do |attrib|
                    attrib['attrib']
                  end
                  attribs
                end
                
                # retrieve a single record if object id provided, otherwise return
                # full list corresponding to factory's source id
                def find(*args)
                  puts "Inside find: args - #{args.inspect}"
                  ret_list = []
                  # do we have conditions? let's make a conditions hash
                  condition_hash = {}
                  select_arr = nil
                  if args[1]
                    condition_hash = args[1][:conditions] if args[1] and args[1][:conditions] and args[1][:conditions].is_a?(Hash)
                    select_arr = args[1][:select] if args[1][:select]
                  end
                  source_id = get_source_id
                  sql = ""
				  # find by :conditions
				  if condition_hash.length > 0
				    #compose SQL for selection of object IDs
				    table = " object_values " # First select will be from object_values table
				    i = 0
					condition_hash.each do |k , v| # add each condition as nested select
					  sql << "SELECT object FROM"
  					  sql << " (" if i > 0  # outer selects are from previous results
					  sql << table
					  sql << ") " if i > 0
					  sql << " WHERE "
					  sql << "attrib ='" << k.to_s << "' AND value='" << v.to_s << "' AND source_id=" << source_id.to_s
					  table = sql.dup
					  i += 1
					end
                  elsif args.first == :all
				  # find :all
					sql << "SELECT distinct(object) FROM object_values WHERE source_id=" << source_id.to_s
                  elsif args.first == :first
				  # find :first
					sql << "SELECT object FROM object_values WHERE source_id=" << source_id.to_s << " LIMIT 1"
                  elsif args.first.is_a?(String)
				  # find by objcet ID
					sql << "SELECT object FROM object_values WHERE source_id=" << source_id.to_s << " AND object='" << strip_braces(args.first.to_s) << "'"
				  end
				  puts "Object Lookup SQL is: #{sql}"
				  start = Time.new
				  objects = ::Rhom::RhomDbAdapter.execute_sql(sql)
				  puts "Object Lookup query took #{Time.new - start} sec, #{objects.length} objects"
				  puts "Objects: #{objects.inspect.to_s}"
				  # Now we have the object IDs, lets find the attributes for each object
				  objects.uniq!
				  start = Time.new
				  objects.each do |object|
				    object_id = object.values[0] 
					sql = "SELECT * from object_values WHERE object='#{object_id}'" 
					object = ::Rhom::RhomDbAdapter.execute_sql(sql)
				    puts "Looked up object (#{object_id}): #{object.inspect.to_s}"
					# create the new object
					new_obj = self.new
					# Add the object attribute first with {}
 				    new_obj.vars.merge!( 'object' => "{#{object_id}}")
					# and add each attribute
					object.uniq!
#					msg = "Added attributes for object (#{object_id}): "
					object.each do |rowhash|
					  new_obj.vars.merge!(rowhash['attrib'] => rowhash['value'])
#					  msg << rowhash['attrib']  + ":" + rowhash['value'] + ";"
					end
					puts "Composed object (#{object_id}): " + object.inspect.to_s
#					puts msg.to_s
					# put it on the list to return
					ret_list << new_obj
				  end
                  puts "Composing rhom objects took #{Time.new - start} sec, #{ret_list.length} objects"
				  start = Time.new
				  order_value = args[1][:order] if args[1] and args[1][:order]
 			      if order_value
				    order_sym = order_value.to_sym
#					ret_list.sort! {|x,y| x.send(order_sym) && y.send(order_sym) ? x.send(order_sym) <=> y.send (order_sym) : 0}
					ret_list.sort!
                    puts "Sorting objects by #{order_value} took #{Time.new - start} sec"
				  end
                  args.first == :first || args.first.is_a?(String) ? ret_list[0] : ret_list
                end
              
                def find_all(args={})
                  find(args)
                end
  
                def set_notification(url,params)
                  SyncEngine.set_notification(get_source_id.to_i,url,params)
                end
                
                def clear_notification
                  SyncEngine.clear_notification(get_source_id.to_i)
                end
                
                def ask(question)
                  tmp_obj = self.new(:object =>djb_hash("#{question}#{rand.to_s}", 10).to_s)
                  if question
                    # We only support one ask at a time!
                    ::Rhom::RhomDbAdapter.delete_from_table('object_values', {"source_id"=>get_source_id, "update_type"=>'ask'})
                    ::Rhom::RhomDbAdapter.insert_into_table('object_values', {"source_id"=>get_source_id, "object"=>tmp_obj.object, "attrib"=>'question', "value"=>Rho::RhoSupport.url_encode(question), "update_type"=>'ask'})
                    SyncEngine.dosync
                  end
                end
                
                # deletes all records matching conditions (optionally nil)
                def delete_all(conditions=nil)
                  if conditions
                    del_conditions = get_conditions_hash(conditions[:conditions])
                    # find all relevant objects, then delete them
                    del_objects = ::Rhom::RhomDbAdapter.select_from_table('object_values', 'object', del_conditions.merge!({"source_id"=>get_source_id}), {"distinct"=>true})
                    del_objects.each do |obj|                                                       
                      ::Rhom::RhomDbAdapter.delete_from_table('object_values', {'object'=>obj['object']})
                    end
                  else
                    ::Rhom::RhomDbAdapter.delete_from_table('object_values', {"source_id"=>get_source_id})
                  end
                end
                    
                private
                # get hash of conditions in sql form
                def get_conditions_hash(conditions=nil)
                  if conditions
                    condition_hash = {}
                    conditions.each do |key,value|
                      condition_hash.merge!('attrib' => key.to_s, 'value' => value.to_s)
                    end
                    condition_hash
                  else
                    nil
                  end
                end
              end #class methods
	
              # deletes the record from the viewable list as well as
              # adding a delete record to the list of sync operations
              def destroy
                result = nil
                obj = self.inst_strip_braces(self.object)
                update_type=self.get_update_type_by_source('delete')
                if obj
                  # first delete the record from viewable list
                  result = ::Rhom::RhomDbAdapter.delete_from_table('object_values', {"object"=>obj})
                  if update_type
                    # now add delete operation
                    result = ::Rhom::RhomDbAdapter.insert_into_table('object_values', {"source_id"=>self.get_inst_source_id, "object"=>obj, "update_type"=>update_type})
                  end
                end
                result
              end
		
              # saves the current object to the database as a create type
              def save
                result = nil
                # iterate over each instance variable and insert create row to table
				        obj = self.inst_strip_braces(self.object)
				        update_type=self.get_update_type_by_source('create')
                self.vars.each do |key,value|
                  val = self.inst_strip_braces(value)
                  # add rows excluding object, source_id and update_type
                  unless self.method_name_reserved?(key)
                    fields = {"source_id"=>self.get_inst_source_id,
                              "object"=>obj,
                              "attrib"=>key,
                              "value"=>val,
                              "update_type"=>update_type}
                    fields = key == "image_uri" ? fields.merge!({"attrib_type" => "blob.file"}) : fields
                    result = ::Rhom::RhomDbAdapter.insert_into_table('object_values', fields)                                     
                  end
                end
                result
              end
          
              # updates the current record in the viewable list and adds
              # a sync operation to update
              def update_attributes(attrs)
                result = nil
                obj = self.inst_strip_braces(self.object)
                update_type=self.get_update_type_by_source('update')
                attrs.each do |attrib,val|
                  attrib = attrib.to_s.gsub(/@/,"")
                  old_val = self.send attrib.to_sym unless self.method_name_reserved?(attrib)
                  
                  # Don't save objects with braces to database
                  new_val = self.inst_strip_braces(val)
                  
                  # if the object's value doesn't match the database record
                  # then we procede with update
                  if old_val != new_val
                    unless self.method_name_reserved?(attrib)
                      # only one update at a time
                      ::Rhom::RhomDbAdapter.delete_from_table('object_values', {"source_id"=>self.get_inst_source_id, "object"=>obj, "attrib"=>attrib, "update_type"=>update_type})
                      # add to syncengine queue
                      ::Rhom::RhomDbAdapter.insert_into_table('object_values', {"source_id"=>self.get_inst_source_id, "object"=>obj, "attrib"=>attrib, "value"=>new_val, "update_type"=>update_type})
                      # update viewable ('query') list
                      ::Rhom::RhomDbAdapter.delete_from_table('object_values', {"source_id"=>self.get_inst_source_id, "object"=>obj, "attrib"=>attrib, "update_type"=>'query'})
                      result = ::Rhom::RhomDbAdapter.insert_into_table('object_values', {"source_id"=>self.get_inst_source_id, "object"=>obj, "attrib"=>attrib, "value"=>new_val, "update_type"=>'query'})
                    end
                  end
                end
                result
              end
	
              def get_inst_source_id
                Rho::RhoConfig.sources[self.class.name.to_s]['source_id'].to_s
              end
              
              def get_update_type_by_source(update_type)
                source_type = Rho::RhoConfig.sources[self.class.name.to_s]['type']
                if source_type and source_type == "ask" and update_type == 'delete'
                  nil
                elsif source_type and source_type == "ask"
                  "query"
                else
                  update_type
                end
              end
              
              def inst_strip_braces(str=nil)
                str ? str.gsub(/\{/,"").gsub(/\}/,"") : nil
              end
            end)
        end
      end
    end
  end # RhomObjectFactory
end # Rhom
